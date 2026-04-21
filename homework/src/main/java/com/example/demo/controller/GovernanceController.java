package com.example.demo.controller;

import com.example.demo.config.GovernanceProperties;
import com.example.demo.service.GovernanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RefreshScope
@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final GovernanceService governanceService;

    private final GovernanceProperties governanceProperties;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private String serverPort;

    @Value("${INSTANCE_NAME:${HOSTNAME:flash-sale-instance}}")
    private String instanceName;

    public GovernanceController(GovernanceService governanceService, GovernanceProperties governanceProperties) {
        this.governanceService = governanceService;
        this.governanceProperties = governanceProperties;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("service", applicationName);
        response.put("instance", instanceName);
        response.put("port", serverPort);
        response.put("banner", governanceProperties.getBanner());
        response.put("time", LocalDateTime.now());
        return response;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("banner", governanceProperties.getBanner());
        response.put("tip", governanceProperties.getTip());
        response.put("unstableDelayMs", governanceProperties.getUnstableDelayMs());
        response.put("fallbackMessage", governanceProperties.getFallbackMessage());
        return response;
    }

    @GetMapping("/hot")
    public Map<String, Object> hot(@RequestParam(defaultValue = "guest") String caller) {
        return governanceService.hotResource(caller);
    }

    @GetMapping("/unstable")
    public Map<String, Object> unstable(@RequestParam(defaultValue = "ok") String mode) {
        return governanceService.unstableResource(mode);
    }
}