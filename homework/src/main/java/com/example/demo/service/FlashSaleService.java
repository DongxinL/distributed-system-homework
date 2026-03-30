package com.example.demo.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FlashSaleService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OrderService orderService;

    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final String RECORD_PREFIX = "seckill:record:"; // 记录用户是否购买过

    // Lua脚本：原子性检查库存并扣减
    // KEYS[1] = key, ARGV[1] = 扣减数量
    // 返回值：-1表示库存不足，其他表示剩余库存
    private static final String SCRIPT = 
            "if (redis.call('exists', KEYS[1]) == 1) then " +
            "   local stock = tonumber(redis.call('get', KEYS[1])); " +
            "   local num = tonumber(ARGV[1]); " +
            "   if (stock >= num) then " +
            "       return redis.call('incrby', KEYS[1], 0 - num); " +
            "   end; " +
            "end; " +
            "return -1;";

    public String processFlashSale(Long userId, Long productId, Integer quantity) {
        // 1. 幂等性校验：防止同一用户重复下单
        String recordKey = RECORD_PREFIX + userId + ":" + productId;
        Boolean isFirstTime = stringRedisTemplate.opsForValue().setIfAbsent(recordKey, "1", 1, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isFirstTime)) {
            log.warn("用户{}重复秒杀商品{}", userId, productId);
            return "REPEAT"; // 重复秒杀
        }

        String key = STOCK_PREFIX + productId;
        
        // 2. Redis预扣减库存 (Lua脚本保证原子性)
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(quantity));

        if (result == null || result < 0) {
            log.warn("用户{}秒杀商品{}失败，库存不足或未初始化", userId, productId);
            // 扣减失败，清理购买记录，允许用户再次尝试（虽然库存大概率已空）
            stringRedisTemplate.delete(recordKey);
            return "SOLD_OUT"; // 库存不足
        }

        // 3. 生成全局唯一的分布式订单ID (基于雪花算法)
        Long orderId = IdWorker.getId();

        // 4. 库存扣减成功，发送消息到Kafka进行异步下单
        log.info("Redis预扣减成功，当前剩余库存: {}，准备发送Kafka消息", result);
        orderService.sendOrderMessage(orderId, userId, productId, quantity);
        
        return "SUCCESS:" + orderId;
    }
}