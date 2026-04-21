package com.example.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RefreshScope
@RestController
@RequestMapping("/gateway")
public class GatewayFallbackController {

    @Value("${gateway.lab.welcome:网关已启动}")
    private String welcome;

    @Value("${gateway.lab.note:请在 Nacos 中修改配置验证动态刷新}")
    private String note;

    @GetMapping("/fallback/governance")
    public Map<String, Object> governanceFallback() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "网关熔断已触发，已返回降级响应");
        response.put("welcome", welcome);
        response.put("note", note);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("welcome", welcome);
        response.put("note", note);
        return response;
    }
}