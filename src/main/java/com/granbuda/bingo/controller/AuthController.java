// src/main/java/com/granbuda/bingo/controller/AuthController.java
package com.granbuda.bingo.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.granbuda.bingo.model.UserEntity;
import com.granbuda.bingo.repository.UserRepository;
import com.granbuda.bingo.security.JwtUtil;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Endpoint para registrar un nuevo usuario
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Recibiendo solicitud de registro para el email: {}", registerRequest.getEmail());

        Optional<UserEntity> existingUser = userRepository.findByEmail(registerRequest.getEmail());
        if (existingUser.isPresent()) {
            log.warn("El email {} ya está en uso.", registerRequest.getEmail());
            return ResponseEntity.badRequest().body("Correo electrónico ya está en uso.");
        }

        // Crear nuevo usuario
        UserEntity user = new UserEntity();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        userRepository.save(user);
        log.info("Usuario {} registrado exitosamente.", user.getUsername());

        return ResponseEntity.ok(new RegisterResponse("Usuario registrado exitosamente. Por favor, inicia sesión."));
    }

    // Endpoint para iniciar sesión
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Recibiendo solicitud de login para el email: {}", loginRequest.getEmail());

        Optional<UserEntity> userOpt = userRepository.findByEmail(loginRequest.getEmail());
        if (userOpt.isEmpty()) {
            log.warn("Intento de login con email inexistente: {}", loginRequest.getEmail());
            return ResponseEntity.status(401).body("Credenciales incorrectas.");
        }

        UserEntity user = userOpt.get();
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.warn("Intento de login fallido para el usuario: {}", user.getUsername());
            return ResponseEntity.status(401).body("Credenciales incorrectas.");
        }

        String token = jwtUtil.generateToken(user.getUsername());
        log.debug("Token JWT generado para el usuario {}", user.getUsername());

        return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
    }

    // Clases internas para solicitudes y respuestas

    @Data
    static class RegisterRequest {
        @NotBlank(message = "El nombre de usuario es obligatorio.")
        private String username;

        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico debe ser válido.")
        private String email;

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.")
        private String password;
    }

    @Data
    static class LoginRequest {
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico debe ser válido.")
        private String email;

        @NotBlank(message = "La contraseña es obligatoria.")
        private String password;
    }

    @Data
    @AllArgsConstructor
    static class AuthResponse {
        private String token;
        private String username;
    }

    @Data
    @AllArgsConstructor
    static class RegisterResponse {
        private String message;
    }
}
