package com.protectoraMascotas.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    @SecurityRequirement(name = "basicAuth")
    public CsrfToken csrf(@Parameter(hidden = true) CsrfToken token) {
        return token;
    }
}
