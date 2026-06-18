package com.merchanthub.web;

import com.merchanthub.dto.AuthDtos.DevTokenRequest;
import com.merchanthub.dto.AuthDtos.DevTokenResponse;
import com.merchanthub.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Dev-only: mints a Supabase-compatible HS256 token for the given email. */
    @PostMapping("/dev-token")
    public DevTokenResponse devToken(@Valid @RequestBody DevTokenRequest req) {
        return authService.devToken(req.email());
    }
}
