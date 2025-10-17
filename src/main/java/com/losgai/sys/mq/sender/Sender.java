package com.losgai.sys.mq.sender;

import com.losgai.sys.entity.ai.AiMessagePair;
import com.losgai.sys.entity.carRental.Car;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Sender {

    private final RabbitTemplate rabbitTemplate;

    // 发送方法，可以自定义 routingKey 和 exchange
    public void sendMessage(String exchange, String routingKey, AiMessagePair message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

    // 发送车辆信息
    public void sendCar(String exchange, String routingKey, Car car) {
        rabbitTemplate.convertAndSend(exchange, routingKey, car);
    }

    // 发送车辆列表信息
    public void sendCars(String exchange, String routingKey, List<Car> cars) {
        rabbitTemplate.convertAndSend(exchange, routingKey, cars);
    }
}