package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
public class FlashSaleService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OrderService orderService;

    private static final String STOCK_PREFIX = "seckill:stock:";

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

    public boolean processFlashSale(Long userId, Long productId, Integer quantity) {
        String key = STOCK_PREFIX + productId;
        
        // 1. Redis预扣减库存 (Lua脚本保证原子性)
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(quantity));

        if (result == null || result < 0) {
            log.warn("用户{}秒杀商品{}失败，库存不足或未初始化", userId, productId);
            return false; // 库存不足
        }

        // 2. 库存扣减成功，发送消息到Kafka进行异步下单
        log.info("Redis预扣减成功，当前剩余库存: {}，准备发送Kafka消息", result);
        orderService.sendOrderMessage(userId, productId, quantity);
        
        return true;
    }
}