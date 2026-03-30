package com.example.demo.service;

import com.example.demo.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaOrderConsumer {

    @Autowired
    private OrderService orderService;

    @KafkaListener(topics = "flash-sale-orders", groupId = "flash-sale-group")
    public void consume(OrderMessage message) {
        log.info("Kafka消费者接收到订单消息: {}", message);
        try {
            orderService.createOrder(message);
        } catch (Exception e) {
            log.error("处理订单消息失败: {}", message, e);
            // 生产环境中，这里应该有死信队列或重试机制
        }
    }
}