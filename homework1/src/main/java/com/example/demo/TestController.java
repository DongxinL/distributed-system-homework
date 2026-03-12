package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    @Value("${server.port}")
    private String serverPort;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @GetMapping("/test")
    public Map<String, String> test() throws UnknownHostException {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Hello from backend service");
        response.put("appName", appName);
        response.put("serverPort", serverPort);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("instance", InetAddress.getLocalHost().getHostName());
        return response;
    }

    @GetMapping("/info")
    public String info() {
        return "Backend service instance on port: " + serverPort;
    }
}
