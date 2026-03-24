package com.example.demo.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.dto.ProductDetailDTO;
import com.example.demo.entity.Inventory;
import com.example.demo.entity.Product;
import com.example.demo.mapper.InventoryMapper;
import com.example.demo.mapper.ProductMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@DS("slave")
public class ProductService {

    // ==================== 缓存常量 ====================
    private static final String PRODUCT_CACHE_PREFIX = "product:detail:";
    private static final String LOCK_PREFIX = "lock:product:detail:";
    /** 缓存基础过期时间：30分钟 */
    private static final long BASE_TTL_SECONDS = 1800;
    /** 随机偏移范围：0~10分钟（防止缓存雪崩） */
    private static final long RANDOM_TTL_RANGE_SECONDS = 600;
    /** 空值缓存过期时间：60秒（防止缓存穿透） */
    private static final long NULL_TTL_SECONDS = 60;
    /** 分布式锁超时时间：10秒 */
    private static final long LOCK_TIMEOUT_SECONDS = 10;
    /** 获取锁重试次数 */
    private static final int LOCK_RETRY_TIMES = 3;
    /** 获取锁重试间隔：200ms */
    private static final long LOCK_RETRY_INTERVAL_MS = 200;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    @Qualifier("redisObjectMapper")
    private ObjectMapper objectMapper;

    /**
     * 获取商品详情（带缓存 + 穿透/击穿/雪崩防护）
     *
     * 流程：
     * 1. 查 Redis 缓存
     *    - 命中空值标记 "" → 返回 null（缓存穿透防护）
     *    - 命中正常数据 → 反序列化返回
     * 2. 缓存未命中 → 尝试获取分布式锁（缓存击穿防护）
     *    - 获取锁成功 → 二次检查缓存 → 查 DB → 写缓存（随机TTL防雪崩）
     *    - 获取锁失败 → 等待重试
     * 3. 重试耗尽 → 降级直接查 DB
     */
    public ProductDetailDTO getProductDetail(Long id) {
        String cacheKey = PRODUCT_CACHE_PREFIX + id;

        // ========== 第一步：查询缓存 ==========
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            // 【缓存穿透防护】空字符串表示该商品确认不存在
            if (cachedJson.isEmpty()) {
                log.info("缓存穿透防护命中：商品ID={} 确认不存在（空值缓存）", id);
                return null;
            }
            // 缓存命中，直接返回
            log.info("缓存命中：商品ID={}", id);
            return deserialize(cachedJson);
        }

        // ========== 第二步：缓存未命中，尝试获取分布式锁 ==========
        String lockKey = LOCK_PREFIX + id;

        for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
            // 【缓存击穿防护】使用 SETNX 实现分布式锁，只允许一个线程重建缓存
            boolean locked = tryLock(lockKey, LOCK_TIMEOUT_SECONDS);

            if (locked) {
                try {
                    // 二次检查缓存（Double Check）：获取锁期间可能已被其他线程重建
                    cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
                    if (cachedJson != null) {
                        log.info("二次检查缓存命中：商品ID={}", id);
                        return cachedJson.isEmpty() ? null : deserialize(cachedJson);
                    }

                    // 查询数据库
                    log.info("缓存未命中，查询数据库：商品ID={}", id);
                    ProductDetailDTO result = queryProductFromDB(id);

                    if (result == null) {
                        // 【缓存穿透防护】商品不存在，缓存空值，设置较短过期时间
                        stringRedisTemplate.opsForValue().set(
                                cacheKey, "", NULL_TTL_SECONDS, TimeUnit.SECONDS
                        );
                        log.info("缓存穿透防护：缓存空值，商品ID={}，TTL={}s", id, NULL_TTL_SECONDS);
                    } else {
                        // 【缓存雪崩防护】使用随机TTL，防止大量缓存同时过期
                        long ttl = BASE_TTL_SECONDS + ThreadLocalRandom.current()
                                .nextLong(RANDOM_TTL_RANGE_SECONDS);
                        stringRedisTemplate.opsForValue().set(
                                cacheKey, serialize(result), ttl, TimeUnit.SECONDS
                        );
                        log.info("缓存重建成功：商品ID={}，TTL={}s（基础{}s + 随机偏移）",
                                id, ttl, BASE_TTL_SECONDS);
                    }
                    return result;
                } finally {
                    // 释放分布式锁
                    unlock(lockKey);
                }
            }

            // 未获取到锁，说明其他线程正在重建缓存，等待后重试
            log.info("缓存击穿防护：未获取到锁，等待重试（第{}次），商品ID={}", i + 1, id);
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // ========== 第三步：重试耗尽，降级直接查询数据库 ==========
        log.warn("获取锁重试耗尽，降级直查数据库：商品ID={}", id);
        return queryProductFromDB(id);
    }

    /**
     * 从数据库查询商品详情（Product + Inventory）
     */
    private ProductDetailDTO queryProductFromDB(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return null;
        }

        // 查询库存
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", id);
        Inventory inventory = inventoryMapper.selectOne(wrapper);

        // 组装 DTO
        ProductDetailDTO dto = new ProductDetailDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setImageUrl(product.getImageUrl());
        dto.setStock(inventory != null ? inventory.getStock() : 0);
        dto.setCreatedAt(product.getCreatedAt());
        return dto;
    }

    // ==================== Redis 分布式锁操作 ====================

    /**
     * 尝试获取分布式锁（SETNX + 超时时间）
     */
    private boolean tryLock(String lockKey, long timeoutSeconds) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放分布式锁
     */
    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

    // ==================== JSON 序列化/反序列化 ====================

    private String serialize(ProductDetailDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }

    private ProductDetailDTO deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProductDetailDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }
}
