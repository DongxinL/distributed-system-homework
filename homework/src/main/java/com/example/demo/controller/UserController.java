package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User loginUser) {
        Map<String, Object> response = new HashMap<>();
        User user = userService.login(loginUser.getUsername(), loginUser.getPassword());
        if (user != null) {
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("user", user);
        } else {
            response.put("success", false);
            response.put("message", "Invalid username or password");
        }
        return response;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        Map<String, Object> response = new HashMap<>();
        if (userService.register(user)) {
            response.put("success", true);
            response.put("message", "Registration successful");
        } else {
            response.put("success", false);
            response.put("message", "Username already exists");
        }
        return response;
    }
}
