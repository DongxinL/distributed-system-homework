package com.example.demo.controller;

import com.example.demo.service.FlashSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private FlashSaleService flashSaleService;

    @PostMapping("/flash-sale")
    public Map<String, Object> flashSale(@RequestParam Long userId, 
                                         @RequestParam Long productId, 
                                         @RequestParam(defaultValue = "1") Integer quantity) {
        Map<String, Object> response = new HashMap<>();
        
        // 实际场景中 userId 应该从 Token/Session 获取，这里为了简化演示直接作为参数传递
        boolean success = flashSaleService.processFlashSale(userId, productId, quantity);
        
        if (success) {
            response.put("success", true);
            response.put("message", "抢购请求已受理，正在排队处理中...");
        } else {
            response.put("success", false);
            response.put("message", "手慢了，商品已被抢光！");
        }
        
        return response;
    }
}