package com.example.demo.controller;

import com.example.demo.entity.Order;
import com.example.demo.service.FlashSaleService;
import com.example.demo.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private FlashSaleService flashSaleService;

    @Autowired
    private OrderService orderService;

    @PostMapping("/flash-sale")
    public Map<String, Object> flashSale(@RequestParam Long userId, 
                                         @RequestParam Long productId, 
                                         @RequestParam(defaultValue = "1") Integer quantity) {
        Map<String, Object> response = new HashMap<>();
        
        // 实际场景中 userId 应该从 Token/Session 获取，这里为了简化演示直接作为参数传递
        String result = flashSaleService.processFlashSale(userId, productId, quantity);
        
        if (result.startsWith("SUCCESS")) {
            String orderId = result.split(":")[1];
            response.put("success", true);
            response.put("message", "抢购请求已受理，正在排队处理中...");
            response.put("orderId", orderId);
        } else if ("REPEAT".equals(result)) {
            response.put("success", false);
            response.put("message", "您已经参与过该商品的秒杀，请勿重复下单！");
        } else {
            response.put("success", false);
            response.put("message", "手慢了，商品已被抢光！");
        }
        
        return response;
    }

    @GetMapping("/user")
    public Map<String, Object> getOrdersByUser(@RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        List<Order> orders = orderService.getOrdersByUserId(userId);
        response.put("success", true);
        response.put("data", orders);
        return response;
    }

    @GetMapping("/detail")
    public Map<String, Object> getOrderById(@RequestParam Long orderId) {
        Map<String, Object> response = new HashMap<>();
        Order order = orderService.getOrderById(orderId);
        if (order != null) {
            response.put("success", true);
            response.put("data", order);
        } else {
            response.put("success", false);
            response.put("message", "订单不存在或还在处理中");
        }
        return response;
    }
}