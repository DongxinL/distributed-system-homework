package com.example.demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.demo.dto.OrderMessage;
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

    /**
     * 发送订单消息到Kafka
     */
    public void sendOrderMessage(Long orderId, Long userId, Long productId, Integer quantity) {
        OrderMessage message = new OrderMessage(orderId, userId, productId, quantity);
        kafkaTemplate.send(TOPIC_FLASH_SALE, message);
        log.info("已发送秒杀订单消息到Kafka: {}", message);
    }

    /**
     * 消费Kafka消息并创建订单（操作主库）
     */
    @DS("master")
    @Transactional
    public void createOrder(OrderMessage message) {
        Long productId = message.getProductId();
        Integer quantity = message.getQuantity();

        // 幂等性兜底：检查订单是否已存在
        if (orderMapper.selectById(message.getOrderId()) != null) {
            log.warn("订单已存在，跳过处理。订单ID: {}", message.getOrderId());
            return;
        }

        // 1. 数据库真实扣减库存（乐观锁/条件更新防止超卖）
        UpdateWrapper<Inventory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("product_id", productId)
                     .ge("stock", quantity)
                     .setSql("stock = stock - " + quantity);
        
        int updated = inventoryMapper.update(null, updateWrapper);
        if (updated == 0) {
            log.warn("MySQL扣减库存失败，可能库存不足。商品ID: {}", productId);
            return; // 扣减失败则不创建订单
        }

        // 2. 获取商品价格
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.error("商品不存在: {}", productId);
            throw new RuntimeException("商品不存在");
        }

        // 3. 创建订单记录
        Order order = new Order();
        order.setId(message.getOrderId()); // 使用雪花算法预先生成的ID
        order.setUserId(message.getUserId());
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalPrice(product.getPrice().multiply(new BigDecimal(quantity)));
        order.setStatus("CREATED");
        
        orderMapper.insert(order);
        log.info("MySQL订单创建成功: 订单ID {}", order.getId());
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