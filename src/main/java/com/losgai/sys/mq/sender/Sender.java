package com.losgai.sys.mq.sender;

import com.losgai.sys.dto.RefundDto;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.entity.carRental.Comment;
import com.losgai.sys.entity.carRental.RentalOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Sender {

    private final RabbitTemplate rabbitTemplate;

    // 发送车辆信息
    public void sendCar(String exchange, String routingKey, Car car) {
        rabbitTemplate.convertAndSend(exchange, routingKey, car);
    }

    // 发送车辆列表信息
    public void sendCars(String exchange, String routingKey, List<Car> cars) {
        rabbitTemplate.convertAndSend(exchange, routingKey, cars);
    }

    // 发送车辆文档删除信息
    public void sendCarDelete(String exchange, String routingKey, Long id) {
        rabbitTemplate.convertAndSend(exchange, routingKey, id);
    }

    // 发送评论审查任务信息
    public void sendCarReview(String exchange, String routingKey, Comment comment) {
        rabbitTemplate.convertAndSend(exchange, routingKey, comment);
    }

    // 发送订单延迟消费信息
    public void sendOrder(String exchange, String routingKey, RentalOrder order) {
        rabbitTemplate.convertAndSend(exchange, routingKey, order);
    }

    // 发送订单退款信息
    public void sendOrderRefund(String exchange, String routingKey, RefundDto refundDto) {
        rabbitTemplate.convertAndSend(exchange, routingKey, refundDto);
    }
}