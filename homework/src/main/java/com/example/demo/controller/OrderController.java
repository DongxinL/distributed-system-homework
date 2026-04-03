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

    /**
     * 发起支付 — 基于消息的一致性保障（支付+订单状态更新）
     */
    @PostMapping("/pay")
    public Map<String, Object> pay(@RequestParam Long orderId, @RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();

        String result = orderService.initiatePayment(orderId, userId);

        if ("SUCCESS".equals(result)) {
            response.put("success", true);
            response.put("message", "支付请求已受理，正在处理中...");
        } else if ("ORDER_NOT_FOUND".equals(result)) {
            response.put("success", false);
            response.put("message", "订单不存在或还在创建中，请稍后再试");
        } else if ("UNAUTHORIZED".equals(result)) {
            response.put("success", false);
            response.put("message", "无权操作此订单");
        } else {
            String status = result.replace("INVALID_STATUS:", "");
            response.put("success", false);
            response.put("message", "订单当前状态为 " + status + "，无法支付");
        }

        return response;
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestParam Long orderId, @RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        boolean success = orderService.cancelOrder(orderId, userId);
        response.put("success", success);
        response.put("message", success ? "订单已取消" : "取消失败，订单可能已支付或不存在");
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