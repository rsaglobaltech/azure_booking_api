package com.booking.azure.infrastructure.adapter.in.web;

import com.booking.azure.infrastructure.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    
    @Value("${app.user.username:admin}")
    private String configuredUsername;

    @Value("${app.user.password:password}")
    private String configuredPassword;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (configuredUsername.equals(username) && configuredPassword.equals(password)) {
            String accessToken = jwtUtil.generateAccessToken(username);
            String refreshToken = jwtUtil.generateRefreshToken(username);
            
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", accessToken);
            tokens.put("refreshToken", refreshToken);
            
            return ResponseEntity.ok(tokens);
        }
        
        return ResponseEntity.status(401).body("Invalid credentials");
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        
        if (refreshToken != null && jwtUtil.validateToken(refreshToken)) {
            String clientId = jwtUtil.extractUsername(refreshToken);
            String newAccessToken = jwtUtil.generateAccessToken(clientId);
            
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", newAccessToken);
            tokens.put("refreshToken", refreshToken);
            
            return ResponseEntity.ok(tokens);
        }
        
        return ResponseEntity.status(401).body("Invalid refresh token");
    }
}


