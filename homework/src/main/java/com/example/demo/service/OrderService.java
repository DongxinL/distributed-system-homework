package com.example.demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.demo.dto.OrderMessage;
import com.example.demo.dto.PaymentMessage;
import com.example.demo.entity.Inventory;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.mapper.InventoryMapper;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_FLASH_SALE = "flash-sale-orders";
    private static final String TOPIC_PAYMENT = "payment-orders";

    /**
     * 发送订单消息到Kafka
     */
    public void sendOrderMessage(Long orderId, Long userId, Long productId, Integer quantity) {
        OrderMessage message = new OrderMessage(orderId, userId, productId, quantity);
        kafkaTemplate.send(TOPIC_FLASH_SALE, message);
        log.info("已发送秒杀订单消息到Kafka: {}", message);
    }

    /**
     * 发送支付消息到Kafka（基于消息的一致性）
     */
    public void sendPaymentMessage(Long orderId, Long userId, BigDecimal amount) {
        PaymentMessage message = new PaymentMessage(orderId, userId, amount);
        kafkaTemplate.send(TOPIC_PAYMENT, message);
        log.info("已发送支付消息到Kafka: {}", message);
    }

    /**
     * 消费Kafka消息并创建订单（操作主库）
     * @return true=创建成功, false=创建失败（需要补偿回滚Redis）
     */
    @DS("master")
    @Transactional
    public boolean createOrder(OrderMessage message) {
        Long productId = message.getProductId();
        Integer quantity = message.getQuantity();

        // 幂等性兜底：检查订单是否已存在
        if (orderMapper.selectById(message.getOrderId()) != null) {
            log.warn("订单已存在，跳过处理。订单ID: {}", message.getOrderId());
            return true; // 已存在视为成功
        }

        // 1. 数据库真实扣减库存（乐观锁/条件更新防止超卖）
        UpdateWrapper<Inventory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("product_id", productId)
                     .ge("stock", quantity)
                     .setSql("stock = stock - " + quantity);
        
        int updated = inventoryMapper.update(null, updateWrapper);
        if (updated == 0) {
            log.warn("MySQL扣减库存失败，可能库存不足。商品ID: {}", productId);
            return false; // 扣减失败，需回滚Redis
        }

        // 2. 获取商品价格
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.error("商品不存在: {}", productId);
            throw new RuntimeException("商品不存在");
        }

        // 3. 创建订单记录
        Order order = new Order();
        order.setId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalPrice(product.getPrice().multiply(new BigDecimal(quantity)));
        order.setStatus("CREATED");
        
        orderMapper.insert(order);
        log.info("MySQL订单创建成功: 订单ID {}", order.getId());
        return true;
    }

    /**
     * 处理支付消息 — 基于消息的一致性保障（订单支付+状态更新）
     */
    @DS("master")
    @Transactional
    public void processPayment(PaymentMessage message) {
        Long orderId = message.getOrderId();

        // 幂等性校验
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("支付处理：订单不存在，订单ID: {}", orderId);
            return;
        }
        if ("PAID".equals(order.getStatus())) {
            log.warn("支付处理：订单已支付，跳过。订单ID: {}", orderId);
            return;
        }
        if (!"CREATED".equals(order.getStatus())) {
            log.warn("支付处理：订单状态不正确（{}），无法支付。订单ID: {}", order.getStatus(), orderId);
            return;
        }

        // 模拟支付网关调用（实际生产中对接第三方支付）
        boolean paymentSuccess = simulatePayment(message);

        if (paymentSuccess) {
            // 更新订单状态为已支付
            UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", orderId)
                         .eq("status", "CREATED")
                         .set("status", "PAID");
            int updated = orderMapper.update(null, updateWrapper);
            if (updated > 0) {
                log.info("订单支付成功，状态已更新为PAID: 订单ID={}", orderId);
            } else {
                log.warn("订单状态更新失败（可能已被其他线程处理）: 订单ID={}", orderId);
            }
        } else {
            markPaymentFailed(orderId);
        }
    }

    /**
     * 标记支付失败
     */
    @DS("master")
    @Transactional
    public void markPaymentFailed(Long orderId) {
        UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", orderId)
                     .in("status", "CREATED", "PAYING")
                     .set("status", "PAYMENT_FAILED");
        int updated = orderMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("订单标记为支付失败: 订单ID={}", orderId);
        }
    }

    /**
     * 模拟支付（生产中替换为真实支付网关调用）
     */
    private boolean simulatePayment(PaymentMessage message) {
        log.info("模拟支付中... 订单ID={}, 金额={}", message.getOrderId(), message.getAmount());
        try {
            Thread.sleep(100); // 模拟支付耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true; // 模拟支付总是成功
    }

    /**
     * 发起支付请求（Controller调用）
     */
    public String initiatePayment(Long orderId, Long userId) {
        Order order = getOrderById(orderId);
        if (order == null) {
            return "ORDER_NOT_FOUND";
        }
        if (!order.getUserId().equals(userId)) {
            return "UNAUTHORIZED";
        }
        if (!"CREATED".equals(order.getStatus())) {
            return "INVALID_STATUS:" + order.getStatus();
        }

        // 发送支付消息到Kafka，异步处理保障一致性
        sendPaymentMessage(orderId, userId, order.getTotalPrice());
        return "SUCCESS";
    }

    /**
     * 取消订单并回滚库存
     */
    @DS("master")
    @Transactional
    public boolean cancelOrder(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return false;
        }
        if (!"CREATED".equals(order.getStatus()) && !"PAYMENT_FAILED".equals(order.getStatus())) {
            return false;
        }

        // 更新订单状态为已取消
        UpdateWrapper<Order> orderUpdate = new UpdateWrapper<>();
        orderUpdate.eq("id", orderId)
                   .in("status", "CREATED", "PAYMENT_FAILED")
                   .set("status", "CANCELLED");
        int updated = orderMapper.update(null, orderUpdate);
        if (updated == 0) {
            return false;
        }

        // 回滚数据库库存
        UpdateWrapper<Inventory> inventoryUpdate = new UpdateWrapper<>();
        inventoryUpdate.eq("product_id", order.getProductId())
                       .setSql("stock = stock + " + order.getQuantity());
        inventoryMapper.update(null, inventoryUpdate);

        log.info("订单已取消并回滚库存: 订单ID={}, 商品ID={}, 数量={}", orderId, order.getProductId(), order.getQuantity());
        return true;
    }

    /**
     * 根据用户ID查询订单
     */
    @DS("slave")
    public List<Order> getOrdersByUserId(Long userId) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        return orderMapper.selectList(wrapper);
    }

    /**
     * 根据订单ID查询订单
     */
    @DS("slave")
    public Order getOrderById(Long orderId) {
        return orderMapper.selectById(orderId);
    }
}