package com.example.demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.example.demo.entity.Inventory;
import com.example.demo.mapper.InventoryMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InventoryPreloadService {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_PREFIX = "seckill:stock:";

    @PostConstruct
    @DS("slave")
    public void init() {
        log.info("开始预热秒杀商品库存到Redis...");
        List<Inventory> inventories = inventoryMapper.selectList(null);
        if (inventories != null) {
            for (Inventory inventory : inventories) {
                stringRedisTemplate.opsForValue().set(STOCK_PREFIX + inventory.getProductId(), String.valueOf(inventory.getStock()));
                log.info("商品ID: {}, 库存: {} 已加载到Redis", inventory.getProductId(), inventory.getStock());
            }
        }
        log.info("库存预热完成！");
    }
}