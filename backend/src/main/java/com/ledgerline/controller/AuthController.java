package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.AuthRequest;
import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = getClientIp(servletRequest);
        AuthResponse response = authService.register(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = getClientIp(servletRequest);
        AuthResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        AuthResponse response = new AuthResponse(
                null,
                principal.getId(),
                principal.getUsername(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole()
        );
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", response));
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
