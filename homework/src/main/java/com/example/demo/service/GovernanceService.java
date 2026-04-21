package com.example.demo.service;

import com.example.demo.config.GovernanceProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GovernanceService {

    private final GovernanceProperties governanceProperties;

    public GovernanceService(GovernanceProperties governanceProperties) {
        this.governanceProperties = governanceProperties;
    }

    public Map<String, Object> hotResource(String caller) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("caller", caller);
        response.put("message", "热点接口访问成功");
        response.put("banner", governanceProperties.getBanner());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    public Map<String, Object> unstableResource(String mode) {
        if ("timeout".equalsIgnoreCase(mode)) {
            sleep(governanceProperties.getUnstableDelayMs());
        }
        if ("error".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("模拟服务异常");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("mode", mode);
        response.put("message", "治理演示接口执行成功");
        response.put("delayMs", governanceProperties.getUnstableDelayMs());
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    private void sleep(long timeoutMs) {
        try {
            Thread.sleep(timeoutMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("线程被中断", ex);
        }
    }
}