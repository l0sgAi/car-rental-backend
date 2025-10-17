package com.losgai.sys.mq.consumer;

import com.losgai.sys.config.RabbitMQAiMessageConfig;
import com.losgai.sys.entity.ai.AiMessagePair;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.global.EsConstants;
import com.losgai.sys.service.ai.AiMessagePairService;
import com.losgai.sys.service.rental.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMessageConsumer {

    private final AiMessagePairService aiMessagePairEsService;

    private final CarService carService;

    @RabbitListener(queues = RabbitMQAiMessageConfig.QUEUE_NAME)
    public void receiveMessage(AiMessagePair message) {
        log.info("[MQ]消费者收到消息：{}", message);
        boolean result = false;
        try {
            result = aiMessagePairEsService.insertAiMessagePairDoc(EsConstants.INDEX_NAME_AI_MSG, message);
            if (result) {
                log.info("[MQ]成功插入 ES，消息ID: {}", message.getId());
            } else {
                log.warn("[MQ]插入 ES 失败，消息ID: {}", message.getId());
            }
        } catch (IOException e) {
            log.error("[MQ]插入 ES 异常，消息ID: {}", message.getId(), e);
        }

    }

    @RabbitListener(queues = RabbitMQAiMessageConfig.QUEUE_NAME_CAR)
    @Description("接收单个车辆信息")
    public void receiveCarMessageAdd(Car message) {
        log.info("[MQ]消费者收到消息：{}", message);
        boolean result;
        try {
            result = carService.insertESDoc(EsConstants.INDEX_NAME_CAR_RENTAL, message);
            if (result) {
                log.info("[MQ]成功插入 ES，消息ID: {}", message.getId());
            } else {
                log.warn("[MQ]插入 ES 失败，消息ID: {}", message.getId());
            }
        } catch (IOException e) {
            log.error("[MQ]插入 ES 异常，消息ID: {}", message.getId(), e);
        }

    }

    @RabbitListener(queues = RabbitMQAiMessageConfig.QUEUE_NAME_CAR_BATCH)
    @Description("接收多个车辆信息")
    public void receiveCarsMessageAdd(List<Car> message) {
        log.info("[MQ]消费者收到消息：{}", message);
        boolean result;
        try {
            result = carService.insertESDocBatch(EsConstants.INDEX_NAME_CAR_RENTAL, message);
            if (result) {
                log.info("[MQ]成功批量插入 ES");
            } else {
                log.warn("[MQ]批量插入 ES 失败");
            }
        } catch (IOException e) {
            log.error("[MQ]批量插入 ES 异常", e);
        }

    }

    @RabbitListener(queues = RabbitMQAiMessageConfig.QUEUE_NAME_CAR_UPDATE)
    @Description("更新多个车辆信息")
    public void receiveCarMessageUpdate(Car message) {
        log.info("[MQ]消费者收到消息：{}", message);
        boolean result;
        try {
            result = carService.updateESDoc(EsConstants.INDEX_NAME_CAR_RENTAL, message);
            if (result) {
                log.info("[MQ]成功批量插入 ES");
            } else {
                log.warn("[MQ]批量插入 ES 失败");
            }
        } catch (IOException e) {
            log.error("[MQ]批量插入 ES 异常", e);
        }

    }

    @RabbitListener(queues = RabbitMQAiMessageConfig.QUEUE_NAME_CAR_DEL)
    @Description("删除车辆信息")
    public void receiveCarMessageDelete(Long message) {
        log.info("[MQ]消费者收到消息：{}", message);
        boolean result;
        try {
            result = carService.deleteESDoc(EsConstants.INDEX_NAME_CAR_RENTAL, String.valueOf(message));
            if (result) {
                log.info("[MQ]成功批量插入 ES");
            } else {
                log.warn("[MQ]批量插入 ES 失败");
            }
        } catch (IOException e) {
            log.error("[MQ]批量插入 ES 异常", e);
        }

    }


}