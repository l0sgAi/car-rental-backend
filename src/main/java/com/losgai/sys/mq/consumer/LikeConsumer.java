package com.losgai.sys.mq.consumer;

import com.losgai.sys.config.RabbitMQMessageConfig;
import com.losgai.sys.dto.CommentHeatDto;
import com.losgai.sys.entity.carRental.Like;
import com.losgai.sys.mapper.CommentIndexMapper;
import com.losgai.sys.mapper.LikeMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeConsumer {

    private final LikeMapper likeMapper;

    private final CommentIndexMapper commentIndexMapper;

    // 内存缓冲队列
    private final BlockingQueue<Like> bufferQueue = new LinkedBlockingQueue<>();

    private final TransactionTemplate transactionTemplate;

    // 参数配置
    private static final int BATCH_SIZE = 900;
    private static final int FLUSH_INTERVAL_SECONDS = 3;

    // JDK 21: 虚拟线程执行器 (专门用于执行 IO 操作)
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 聚合线程 (单线程即可，因为它只负责分发任务，不负责执行)
    private final ExecutorService aggregatorExecutor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        // 启动聚合调度器
        aggregatorExecutor.submit(this::aggregationLoop);
    }

    @RabbitListener(queues = RabbitMQMessageConfig.QUEUE_NAME_LIKE)
    public void handleMessage(Like message) {
        // 生产者极快，不阻塞
        bufferQueue.offer(message);
    }

    /**
     * 聚合循环 (长期运行)
     */
    private void aggregationLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<Like> batch = new ArrayList<>();

                // 1. 带超时的获取 (实现时间窗口聚合)
                Like first = bufferQueue.poll(FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);

                if (first != null) {
                    batch.add(first);
                    // 2. 尽可能多捞，直到满了
                    bufferQueue.drainTo(batch, BATCH_SIZE - 1);
                }

                // 3. 如果有数据，提交给虚拟线程去执行 DB 操作
                if (!batch.isEmpty()) {
                    // 关键点：这里不再同步等待 processBatch 完成
                    // 而是“发后即忘”，让虚拟线程去扛 IO
                    // 注意：需要拷贝一份 list，因为 batch 会被清空重用
                    List<Like> taskList = new ArrayList<>(batch);

                    ioExecutor.submit(() -> processBatch(taskList));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("聚合循环异常", e);
            }
        }
    }

    /**
     * 耗时的 IO 操作
     */
    private void processBatch(List<Like> messages) {
        try {
            // 聚合点赞列表，对于相同的userId和commentId根据最后操作时间时间来聚合
            // Key: "userId_commentId", Value: Like对象
            Map<String, Like> uniqueMap = new HashMap<>();

            for (Like item : messages) {
                String key = item.getUserId() + "_" + item.getCommentId();
                // 直接覆盖，保留最后一次的状态
                uniqueMap.put(key, item);
            }

            // 拿到去重后的列表
            List<Like> optimizedList = new ArrayList<>(uniqueMap.values());
            log.debug("虚拟线程 {} 开始执行批量写入，条数: {}", Thread.currentThread(), optimizedList.size());
            long start = System.currentTimeMillis();

            // 使用map，聚合点赞变化值，根据commentId聚合，isFallback=0为增，1为减
            List<CommentHeatDto> heatDtoList = calculateHeatDelta(messages)
                    .entrySet()
                    .stream()
                    .map(i-> new CommentHeatDto(i.getKey(),i.getValue())).toList();

            // MyBatis 批量写入 (阻塞 IO)
            // 在 JDK 21 下，虚拟线程遇到这种阻塞会自动挂起，不消耗 OS 线程
            // 业务逻辑成功，隐式提交
            // 开启事务
            transactionTemplate.execute(status -> {
                likeMapper.batchInsert(optimizedList);
                commentIndexMapper.batchUpdateHeat(heatDtoList);
                return null;
            });
            log.debug("写入完成，耗时: {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("批量写入失败", e);
            // 这里可以做重试逻辑
        }
    }

    /**
     * 聚合评论热度变化值
     * @param rawList 原始消息列表
     * @return Map<CommentId, DeltaValue>
     */
    private Map<Long, Integer> calculateHeatDelta(List<Like> rawList) {
        Map<Long, Integer> deltaMap = new HashMap<>();

        for (Like like : rawList) {
            Long commentId = like.getCommentId();

            // 根据你的业务定义：
            // isFallback = 0 (点赞) -> +1
            // isFallback = 1 (取消) -> -1
            int change = (like.getIsFallback() == 0) ? 1 : -1;

            // merge 方法：如果 Key 不存在则放入 change，存在则执行 oldValue + change
            deltaMap.merge(commentId, change, Integer::sum);
        }

        // 过滤掉 value 为 0 的项（如果 +1 又 -1 抵消了，就没必要去数据库操作了）
        // 使用 Java 8 removeIf
        deltaMap.values().removeIf(value -> value == 0);

        return deltaMap;
    }

    @PreDestroy
    public void destroy() {
        // 优雅关闭
        aggregatorExecutor.shutdownNow();
        ioExecutor.close(); // JDK 21 新增的 close 方法，会等待任务完成
    }
}