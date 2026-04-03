package com.example.demo.service;

import com.example.demo.dto.OrderMessage;
import com.example.demo.dto.PaymentMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaOrderConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final String RECORD_PREFIX = "seckill:record:";

    @KafkaListener(topics = "flash-sale-orders", groupId = "flash-sale-group")
    public void consume(OrderMessage message) {
        log.info("Kafka消费者接收到订单消息: {}", message);
        try {
            boolean success = orderService.createOrder(message);
            if (!success) {
                // MySQL扣减库存失败，补偿回滚Redis库存 + 清理限购记录
                rollbackRedisStock(message);
            }
        } catch (Exception e) {
            log.error("处理订单消息失败，执行补偿回滚: {}", message, e);
            rollbackRedisStock(message);
        }
    }

    @KafkaListener(topics = "payment-orders", groupId = "payment-group")
    public void consumePayment(PaymentMessage message) {
        log.info("Kafka消费者接收到支付消息: {}", message);
        try {
            orderService.processPayment(message);
        } catch (Exception e) {
            log.error("处理支付消息失败: {}", message, e);
            // 支付失败，标记订单为支付失败
            try {
                orderService.markPaymentFailed(message.getOrderId());
            } catch (Exception ex) {
                log.error("标记支付失败也失败，需人工介入，订单ID: {}", message.getOrderId(), ex);
            }
        }
    }

    /**
     * 补偿机制：回滚Redis库存 + 清理限购记录
     */
    private void rollbackRedisStock(OrderMessage message) {
        try {
            String stockKey = STOCK_PREFIX + message.getProductId();
            stringRedisTemplate.opsForValue().increment(stockKey, message.getQuantity());
            log.info("Redis库存已回滚: 商品ID={}, 回滚数量={}", message.getProductId(), message.getQuantity());

            String recordKey = RECORD_PREFIX + message.getUserId() + ":" + message.getProductId();
            stringRedisTemplate.delete(recordKey);
            log.info("限购记录已清理: 用户ID={}, 商品ID={}", message.getUserId(), message.getProductId());
        } catch (Exception e) {
            log.error("Redis库存回滚失败，需人工介入！消息: {}", message, e);
        }
    }
}