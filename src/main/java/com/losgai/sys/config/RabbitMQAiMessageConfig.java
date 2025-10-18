package com.losgai.sys.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RabbitMQAiMessageConfig {

    public static final String EXCHANGE_NAME = "sys.exchange";

    public static final String QUEUE_NAME = "sys.message.queue";
    public static final String ROUTING_KEY = "sys.message";

    // 单个插入
    public static final String QUEUE_NAME_CAR = "sys.car.queue";
    public static final String ROUTING_KEY_CAR = "sys.car";

    // 批量插入
    public static final String QUEUE_NAME_CAR_BATCH = "sys.cars.queue";
    public static final String ROUTING_KEY_CAR_BATCH = "sys.cars";

    // 单个更新
    public static final String QUEUE_NAME_CAR_UPDATE = "sys.car.update.queue";
    public static final String ROUTING_KEY_CAR_UPDATE = "sys.car.update";

    // 单个删除
    public static final String QUEUE_NAME_CAR_DEL = "sys.car.delete.queue";
    public static final String ROUTING_KEY_CAR_DEL = "sys.car.delete";

    // 单个删除
    public static final String QUEUE_NAME_COMMENT_CENSOR = "sys.comment.censor.queue";
    public static final String ROUTING_KEY_COMMENT_CENSOR = "sys.comment.censor";

    // 声明交换机
    @Bean
    public DirectExchange exchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME).durable(true).build();
    }

    //  第一个队列（普通消息）
    @Bean
    public Queue messageQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    // 第二个队列（车辆消息）
    @Bean
    public Queue carQueue() {
        return QueueBuilder.durable(QUEUE_NAME_CAR).build();
    }

    @Bean
    public Queue carsQueue() {
        return QueueBuilder.durable(QUEUE_NAME_CAR_BATCH).build();
    }

    @Bean
    public Queue carUpdateQueue() {
        return QueueBuilder.durable(QUEUE_NAME_CAR_UPDATE).build();
    }

    @Bean
    public Queue carDeleteQueue() {
        return QueueBuilder.durable(QUEUE_NAME_CAR_DEL).build();
    }

    @Bean
    public Queue commentCensorQueue() {
        return QueueBuilder.durable(QUEUE_NAME_COMMENT_CENSOR).build();
    }

    // 第一个绑定：sys.message -> sys.message.queue
    @Bean
    public Binding bindingMessage(Queue messageQueue, DirectExchange exchange) {
        return BindingBuilder.bind(messageQueue).to(exchange).with(ROUTING_KEY);
    }

    // 第二个绑定：sys.car -> sys.car.queue
    @Bean
    public Binding bindingCar(Queue carQueue, DirectExchange exchange) {
        return BindingBuilder.bind(carQueue).to(exchange).with(ROUTING_KEY_CAR);
    }

    @Bean
    public Binding bindingCars(Queue carsQueue, DirectExchange exchange) {
        return BindingBuilder.bind(carsQueue).to(exchange).with(ROUTING_KEY_CAR_BATCH);
    }

    @Bean
    public Binding bindingCarUpdate(Queue carUpdateQueue, DirectExchange exchange) {
        return BindingBuilder.bind(carUpdateQueue).to(exchange).with(ROUTING_KEY_CAR_UPDATE);
    }

    @Bean
    public Binding bindingCarDelete(Queue carDeleteQueue, DirectExchange exchange) {
        return BindingBuilder.bind(carDeleteQueue).to(exchange).with(ROUTING_KEY_CAR_DEL);
    }

    @Bean
    public Binding bindingCommentCensor(Queue commentCensorQueue, DirectExchange exchange) {
        return BindingBuilder.bind(commentCensorQueue).to(exchange).with(ROUTING_KEY_COMMENT_CENSOR);
    }

    // 反序列化配置
    @Bean
    public SimpleMessageConverter converter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of("com.losgai.sys.entity.*", "java.util.*"));
        return converter;
    }

}