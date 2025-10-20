package com.losgai.sys.service.rental.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.losgai.sys.config.RabbitMQAiMessageConfig;
import com.losgai.sys.dto.CarDocument;
import com.losgai.sys.dto.CarSearchParam;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.global.EsConstants;
import com.losgai.sys.mapper.CarMapper;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.CarService;
import com.losgai.sys.util.ElasticsearchIndexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarServiceImpl implements CarService {

    private final CarMapper carMapper;

    private final ElasticsearchClient esClient;

    private final Sender sender;

    private final String HOT_KEY_PREFIX = "car:hot:";
    private final String HOT_SYNC_SET = "car:hot:sync";

    private final RedisTemplate<String, Object> redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Car> query(String keyWord,Integer status) {
        return carMapper.query(keyWord,status);
    }

    @Override
    public List<Car> globalQuery(CarSearchParam carSearchParam) {
        try {
            // 构建查询请求
            SearchRequest searchRequest = buildSearchRequest(carSearchParam);

            // 执行查询
            SearchResponse<Car> response = esClient.search(searchRequest, Car.class);

            // 提取结果
            List<Car> carList = new ArrayList<>();
            for (Hit<Car> hit : response.hits().hits()) {
                Car car = hit.source();
                if (car != null) {
                    carList.add(car);
                }
            }

            log.info("ES查询结果数量: {}", carList.size());
            return carList;

        } catch (Exception e) {
            log.error("ES查询失败", e);
            throw new RuntimeException("查询车辆信息失败", e);
        }
    }

    @Override
    public ResultCodeEnum add(Car car) {
        car.setDeleted(0);
        car.setCreateTime(Date.from(Instant.now()));
        car.setUpdateTime(Date.from(Instant.now()));
        carMapper.insert(car);
        if (car.getStatus() == 0) {
            // 通过消息队列，添加到ES中
            sender.sendCar(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                    RabbitMQAiMessageConfig.ROUTING_KEY_CAR,
                    car);
        }
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public ResultCodeEnum update(Car car) {
        car.setUpdateTime(Date.from(Instant.now()));
        carMapper.updateByPrimaryKeySelective(car);
        if (car.getStatus() == 0) {
            // 通过消息队列，添加到ES中
            sender.sendCar(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                    RabbitMQAiMessageConfig.ROUTING_KEY_CAR_UPDATE,
                    car);
        } else if (car.getStatus() == 1) {
            // 通过消息队列，删除ES中的文档
            sender.sendCarDelete(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                    RabbitMQAiMessageConfig.ROUTING_KEY_CAR_DEL,
                    car.getId());
        }
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Transactional
    public ResultCodeEnum delete(Long id) {
        carMapper.deleteByPrimaryKey(id);
        carMapper.deleteOrdersByCarId(id);
        carMapper.deleteCommentsByCarId(id);
        // 通过消息队列，删除ES中的文档
        sender.sendCarDelete(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                RabbitMQAiMessageConfig.ROUTING_KEY_CAR_DEL,
                id);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Description("上架所有可租车辆至ES")
    public ResultCodeEnum up() {
        // 获取所有可租车辆列表
        List<Car> cars = carMapper.getAllCanRentCars();
        // 通过消息队列，添加到ES中
        sender.sendCars(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                RabbitMQAiMessageConfig.ROUTING_KEY_CAR_BATCH,
                cars);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Description("文档批量插入Elasticsearch，返回状态true/false")
    public boolean insertESDocBatch(String indexName, List<Car> cars) throws IOException {
        // 1.判断是否有索引，没有则创建
        ElasticsearchIndexUtils.createCarIndex(esClient);

        // 2.转换为CarDocument并插入
        List<BulkOperation> bulkOperations = cars.stream()
                .map(CarDocument::fromCar)
                .map(carDoc ->
                        BulkOperation.of(builder ->
                                builder.index(i -> i.index(indexName)
                                        .document(carDoc)
                                        .id(String.valueOf(carDoc.getId())))))
                .toList();

        BulkResponse response = esClient.bulk(e -> e.index(indexName).operations(bulkOperations));
        log.info("ES批量插入操作完成，耗时{}毫秒", response.took());

        if (response.errors()) {
            log.error("ES批量操作-有错误发生");
            return false;
        }
        return true;
    }

    @Override
    @Description("文档单条插入Elasticsearch，返回状态true/false")
    public boolean insertESDoc(String indexName, Car car) throws IOException {
        // 1.判断是否有索引，没有则创建
        ElasticsearchIndexUtils.createCarIndex(esClient);

        // 2.转换为CarDocument并插入单条文档
        CarDocument carDoc = CarDocument.fromCar(car);

        IndexRequest<CarDocument> indexRequest = IndexRequest.of(builder -> builder
                .index(indexName)
                .document(carDoc)
                .id(String.valueOf(carDoc.getId()))
        );

        IndexResponse response = esClient.index(indexRequest);
        log.info("插入文档成功，ID: {}, 状态: {}", response.id(), response.result().jsonValue());
        return true;
    }

    @Override
    @Description("异步更新Elasticsearch文档")
    public boolean updateESDoc(String indexName, Car car) throws IOException {
        try {
            CarDocument carDoc = CarDocument.fromCar(car);

            // 使用update API
            UpdateRequest<CarDocument, CarDocument> updateRequest = UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(String.valueOf(car.getId()))
                    .retryOnConflict(3)
                    .doc(carDoc)  // 更新的文档
                    .docAsUpsert(true)  // 如果文档不存在则插入
            );

            UpdateResponse<CarDocument> response = esClient.update(updateRequest, CarDocument.class);
            log.info("ES文档更新成功，ID: {}, 状态: {}", response.id(), response.result().jsonValue());
            return true;
        } catch (Exception e) {
            log.error("ES文档更新失败, carId: {}", car.getId(), e);
            return false;
        }
    }

    /**
     * 根据文档ID从Elasticsearch删除单条文档，返回状态true/false
     *
     * @param indexName 索引名称
     * @param docId     要删除的文档ID
     * @return 操作是否成功 (删除成功或文档本不存在都返回true)
     * @throws IOException ES连接或操作异常
     */
    @Override
    @Description("根据文档ID从Elasticsearch删除单条文档，返回状态true/false")
    public boolean deleteESDoc(String indexName, String docId) throws IOException {
        // 1. 构建删除请求
        DeleteRequest deleteRequest = DeleteRequest.of(d -> d
                .index(indexName)
                .id(docId)
        );

        // 2. 执行删除操作
        DeleteResponse response = esClient.delete(deleteRequest);

        // 3. 根据响应结果判断并记录日志
        if (response.result() == Result.Deleted) {
            log.info("文档删除成功, Index: {}, ID: {}, 状态: {}", indexName, response.id(), response.result().jsonValue());
            return true;
        } else if (response.result() == Result.NotFound) {
            // 如果文档不存在，也认为操作达到了目的（确保其不存在），返回true
            log.warn("尝试删除的文档不存在, Index: {}, ID: {}, 状态: {}", indexName, response.id(), response.result().jsonValue());
            return true;
        } else {
            // 其他情况，如版本冲突等，视为失败
            log.error("文档删除失败, Index: {}, ID: {}, 状态: {}", indexName, response.id(), response.result().jsonValue());
            return false;
        }
    }

    @Override
    public Car getCarById(Long id) {
        Car car = carMapper.selectByPrimaryKey(id);

        // Redis 累加热度（最大 50000）
        String key = HOT_KEY_PREFIX + id;
        // 这里指的是上次同步后新增的热度值
        Long currentHot = redisTemplate.opsForValue().increment(key, 1);

        if (currentHot != null && currentHot <= 50000) {
            redisTemplate.opsForSet().add(HOT_SYNC_SET, id); // 标记这个ID待同步
        }

        return car;
    }

    // 定时任务，每 625 秒同步一次热度
    @Scheduled(fixedDelay = 625000)
    public void syncHotScore() {
        Set<Object> carIds = redisTemplate.opsForSet().members(HOT_SYNC_SET);
        if (carIds == null || carIds.isEmpty()) return;

        for (Object idObj : carIds) {
            Long carId = Long.valueOf(idObj.toString());
            String key = HOT_KEY_PREFIX + carId;
            Object hotObj = redisTemplate.opsForValue().get(key);
            if (hotObj == null) continue;
            // 要新增的hotScore
            int hot = Integer.parseInt(hotObj.toString());
            carMapper.updateHotScore(carId, hot); // 批量更新热度
            // 同时还要更新ES
            Car car = carMapper.selectByPrimaryKey(carId);
            sender.sendCar(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                    RabbitMQAiMessageConfig.ROUTING_KEY_CAR_UPDATE,car);

            // 清空刚才同步的热度
            redisTemplate.delete(key);

            log.info("同步热度, carId: {}, hot: {}", carId, hot);

            redisTemplate.opsForSet().remove(HOT_SYNC_SET, carId);
        }
    }

    /**
     * 构建ES查询请求
     */
    private SearchRequest buildSearchRequest(CarSearchParam param) {
        return SearchRequest.of(s -> s
                .index(EsConstants.INDEX_NAME_CAR_RENTAL)  // "/car" 索引
                .query(buildQuery(param))                   // 构建查询条件
                .sort(buildSort(param))                     // 构建排序条件
                .size(1000)                           // 默认返回1000条，可根据需求调整
        );
    }

    /**
     * 构建查询条件
     */
    private Query buildQuery(CarSearchParam param) {
        // 1. 创建一个Bool查询构建器，这是组合所有条件的核心
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        // 2. 处理关键字搜索 (放入 must 子句，影响评分)
        String keyWord = param.getKeyWord();
        if (StrUtil.isNotBlank(keyWord)) {
            // 如果有关键字，构建 multi_match 查询
            Query multiMatchQuery = Query.of(q -> q
                    .multiMatch(m -> m
                            .query(keyWord)
                            .fields("name^3", "number^2", "carType", "powerType")
                            .type(TextQueryType.BestFields)
                            .operator(Operator.Or)
                    )
            );
            // 将 multi_match 查询添加到 bool 查询的 must 子句中
            boolQueryBuilder.must(multiMatchQuery);
        } else {
            boolQueryBuilder.must(q -> q.matchAll(m -> m));
        }

        // 3. 处理筛选条件 (放入 filter 子句，不影响评分，且性能更好)
        List<Query> filterQueries = new ArrayList<>();

        // 筛选 - 车型 (carType)
        if (StrUtil.isNotBlank(param.getCarType())) {
            filterQueries.add(Query.of(q -> q
                    .term(t -> t
                            .field("carType") // 假设 carType 在ES中是 keyword 类型
                            .value(param.getCarType())
                    )
            ));
        }

        // 筛选 - 动力类型 (powerType)
        if (StrUtil.isNotBlank(param.getPowerType())) {
            filterQueries.add(Query.of(q -> q
                    .term(t -> t
                            .field("powerType") // 假设 powerType 在ES中是 keyword 类型
                            .value(param.getPowerType())
                    )
            ));
        }

        // 筛选 - 品牌ID (brandId) - 虽然你没问，但你的参数里有，这是一个很常见的筛选
        if (param.getBrandId() != null) {
            filterQueries.add(Query.of(q -> q
                    .term(t -> t
                            .field("brandId")
                            .value(param.getBrandId())
                    )
            ));
        }

        // 筛选 - 价格区间 (dailyRent)
        Integer minPrice = param.getMinimPrice();
        Integer maxPrice = param.getMaxPrice();

        // 只有在最小或最大价格至少有一个存在时，才添加范围查询
        if (minPrice != null || maxPrice != null) {
            RangeQuery.Builder rangeQueryBuilder = new RangeQuery.Builder();
            rangeQueryBuilder.number(n -> {
                n.field("dailyRent");
                if (minPrice != null) {
                    n.gte(Double.valueOf(minPrice));
                }
                if (maxPrice != null) {
                    n.lte(Double.valueOf(maxPrice));
                }
                return n;
            });
            filterQueries.add(new Query(rangeQueryBuilder.build()));
        }

        // 4. 将所有筛选条件添加到 bool 查询的 filter 子句中
        if (!filterQueries.isEmpty()) {
            boolQueryBuilder.filter(filterQueries);
        }

        // 如果没有任何 must 或 filter 条件，一个空的 bool 查询等价于 match_all
        // 如果只有一个空的 keyword，但有 filter 条件，它将只执行过滤，这是正确的行为
        if (StrUtil.isBlank(keyWord) && filterQueries.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        // 5. 构建并返回最终的 Bool 查询
        return Query.of(q -> q.bool(boolQueryBuilder.build()));
    }

    /**
     * 构建排序条件
     */
    private List<SortOptions> buildSort(CarSearchParam param) {
        List<SortOptions> sortList = new ArrayList<>();

        // 按平均评分排序
        if (param.getAvgScore() != null) {
            sortList.add(buildSortOption("avgScore", param.getAvgScore()));
        }

        // 按热度排序
        if (param.getHotScore() != null) {
            sortList.add(buildSortOption("hotScore", param.getHotScore()));
        }

        // 按油耗排序
        if (param.getFuelConsumption() != null) {
            sortList.add(buildSortOption("fuelConsumption", param.getFuelConsumption()));
        }

        // 按日租金排序
        if (param.getDailyRent() != null) {
            sortList.add(buildSortOption("dailyRent", param.getDailyRent()));
        }

        // 按座位数排序
        if (param.getSeat() != null) {
            sortList.add(buildSortOption("seat", param.getSeat()));
        }

        // 如果没有任何排序条件，默认按相关性评分排序
        if (sortList.isEmpty()) {
            sortList.add(SortOptions.of(s -> s
                    .score(sc -> sc.order(SortOrder.Desc))
            ));
        }

        return sortList;
    }

    /**
     * 构建单个排序选项
     *
     * @param fieldName 字段名
     * @param order     0=正序(ASC), 1=倒序(DESC)
     */
    private SortOptions buildSortOption(String fieldName, Integer order) {
        SortOrder sortOrder = (order != null && order == 0) ? SortOrder.Asc : SortOrder.Desc;
        return SortOptions.of(s -> s
                .field(f -> f
                        .field(fieldName)
                        .order(sortOrder)
                )
        );
    }

}
