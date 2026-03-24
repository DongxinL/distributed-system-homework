package com.example.demo;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    @Value("${server.port}")
    private String serverPort;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @GetMapping("/hello")
    public String hello() {
        return "Hello World!";
    }

    @GetMapping("/replication-status")
    @DS("slave")
    public Map<String, Object> getReplicationStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 在MySQL 8.0.22及以后推荐使用SHOW REPLICA STATUS，但兼容性考虑先用SHOW SLAVE STATUS
            List<Map<String, Object>> statusList = jdbcTemplate.queryForList("SHOW SLAVE STATUS");
            if (!statusList.isEmpty()) {
                Map<String, Object> status = statusList.get(0);
                result.put("Slave_IO_Running", status.get("Slave_IO_Running"));
                result.put("Slave_SQL_Running", status.get("Slave_SQL_Running"));
                result.put("Seconds_Behind_Master", status.get("Seconds_Behind_Master"));
                result.put("status", "ok");
            } else {
                result.put("status", "error");
                result.put("message", "Not a replica or no replica status found");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/check-consistency")
    public Map<String, Object> checkConsistency() {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer masterCount = getMasterUserCount();
            Integer slaveCount = getSlaveUserCount();
            result.put("masterCount", masterCount);
            result.put("slaveCount", slaveCount);
            result.put("consistent", masterCount.equals(slaveCount));
            result.put("status", "ok");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @DS("master")
    public Integer getMasterUserCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    @DS("slave")
    public Integer getSlaveUserCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }
}
