package com.merchanthub.web;

import com.merchanthub.dto.AuthDtos.AuthResponse;
import com.merchanthub.dto.AuthDtos.LoginRequest;
import com.merchanthub.dto.AuthDtos.RegisterRequest;
import com.merchanthub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req.name(), req.email(), req.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }
}
