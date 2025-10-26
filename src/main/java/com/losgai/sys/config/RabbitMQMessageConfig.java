package com.losgai.sys.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQMessageConfig {

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

    // 评论审核
    public static final String QUEUE_NAME_COMMENT_CENSOR = "sys.comment.censor.queue";
    public static final String ROUTING_KEY_COMMENT_CENSOR = "sys.comment.censor";

    // 延迟队列（消息在这里等待过期）
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ROUTING_KEY_ORDER_DELAY = "order.delay";

    // 定义处理队列和其路由键
    public static final String ORDER_PROCESS_QUEUE = "order.process.queue";
    public static final String ROUTING_KEY_ORDER_PROCESS = "order.process";

    // 延迟时间，30分钟 = 30 * 60 * 1000 = 1,800,000毫秒
    private static final long DELAY_TIME = 30 * 60 * 1000;

    // 配置退款队列
    public static final String QUEUE_NAME_REFUND = "refund.queue";
    public static final String ROUTING_KEY_REFUND = "refund";

    /// 声明交换机
    @Bean
    public DirectExchange exchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME).durable(true).build();
    }

    ///  队列声明

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

    // 延迟队列
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", DELAY_TIME);
        args.put("x-dead-letter-exchange", EXCHANGE_NAME);
        args.put("x-dead-letter-routing-key", ROUTING_KEY_ORDER_PROCESS);
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).withArguments(args).build();
    }

    // 处理队列
    @Bean
    public Queue orderProcessQueue() {
        return QueueBuilder.durable(ORDER_PROCESS_QUEUE).build();
    }

    @Bean
    public Queue refundQueue() {
        return QueueBuilder.durable(QUEUE_NAME_REFUND).build();
    }

    /// 绑定

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

    // 延迟队列绑定
    @Bean
    public Binding delayBinding() {
        return BindingBuilder
                .bind(orderDelayQueue())
                .to(exchange())
                .with(ROUTING_KEY_ORDER_DELAY);
    }

    // 处理队列绑定
    @Bean
    public Binding processBinding() {
        return BindingBuilder
                .bind(orderProcessQueue())
                .to(exchange())
                .with(ROUTING_KEY_ORDER_PROCESS);
    }

    @Bean
    public Binding refundBinding() {
        return BindingBuilder
                .bind(refundQueue())
                .to(exchange())
                .with(ROUTING_KEY_REFUND);
    }

    // 反序列化配置
//    @Bean
//    public SimpleMessageConverter converter() {
//        SimpleMessageConverter converter = new SimpleMessageConverter();
//        converter.setAllowedListPatterns(List.of("com.losgai.sys.entity.*", "java.util.*"));
//        return converter;
//    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

}