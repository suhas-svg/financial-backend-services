// // src/main/java/com/suhasan/finance/account_service/controller/AuthController.java
// package com.suhasan.finance.account_service.controller;

// import com.suhasan.finance.account_service.dto.AuthRequest;
// import com.suhasan.finance.account_service.dto.AuthResponse;
// import com.suhasan.finance.account_service.dto.RegisterRequest;
// import com.suhasan.finance.account_service.dto.RegisterResponse;
// import com.suhasan.finance.account_service.service.AuthService;
// import com.suhasan.finance.account_service.security.JwtTokenProvider;
// import jakarta.validation.Valid;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.*;
// import org.springframework.security.authentication.*;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.security.core.Authentication; // for Authentication

// @RestController
// @RequestMapping("/api/auth")
// @RequiredArgsConstructor
// public class AuthController {

//     private final AuthenticationManager authManager;
//     private final JwtTokenProvider tokenProvider;
//     private final AuthService authService;

//     @PostMapping("/login")
//     public ResponseEntity<AuthResponse> login(
//             @Valid @RequestBody AuthRequest req) {
//         Authentication auth = authManager.authenticate(
//             new UsernamePasswordAuthenticationToken(
//                 req.getUsername(), req.getPassword()
//             )
//         );
//         String token = tokenProvider.generateToken(auth);
//         return ResponseEntity.ok(new AuthResponse(token));
//     }

//     @PostMapping("/register")
//     public ResponseEntity<RegisterResponse> register(
//             @Valid @RequestBody RegisterRequest req) {
//         RegisterResponse resp = authService.register(req);
//         return ResponseEntity
//                 .status(HttpStatus.CREATED)
//                 .body(resp);
//     }
// }

// package com.suhasan.finance.account_service.controller;

// import com.suhasan.finance.account_service.dto.AuthRequest;
// import com.suhasan.finance.account_service.dto.AuthResponse;
// import com.suhasan.finance.account_service.dto.RegisterRequest;
// import com.suhasan.finance.account_service.dto.RegisterResponse;
// import com.suhasan.finance.account_service.entity.Role;
// import com.suhasan.finance.account_service.entity.User;
// import com.suhasan.finance.account_service.repository.RoleRepository;
// import com.suhasan.finance.account_service.repository.UserRepository;
// import com.suhasan.finance.account_service.security.JwtTokenProvider;
// import jakarta.validation.Valid;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.Authentication;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.web.bind.annotation.*;

// import java.util.Collections;

// @RestController
// @RequestMapping("/api/auth")
// @RequiredArgsConstructor
// public class AuthController {

//     private final AuthenticationManager authManager;
//     private final JwtTokenProvider tokenProvider;
//     private final UserRepository userRepository;
//     private final RoleRepository roleRepository;
//     private final PasswordEncoder passwordEncoder;

//     @PostMapping("/login")
//     public ResponseEntity<AuthResponse> login(
//             @Valid @RequestBody AuthRequest req) {
//         Authentication auth = authManager.authenticate(
//             new UsernamePasswordAuthenticationToken(
//                 req.getUsername(), req.getPassword()
//             )
//         );
//         String token = tokenProvider.generateToken(auth);
//         return ResponseEntity.ok(new AuthResponse(token));
//     }

//     @PostMapping("/register")
//     public ResponseEntity<RegisterResponse> register(
//             @Valid @RequestBody RegisterRequest req) {
//         if (userRepository.existsByUsername(req.getUsername())) {
//             return ResponseEntity
//                 .status(HttpStatus.BAD_REQUEST)
//                 .body(new RegisterResponse("Username is already taken"));
//         }

//         // create and save the new user
//         User user = new User();
//         user.setUsername(req.getUsername());
//         user.setPassword(passwordEncoder.encode(req.getPassword()));

//         // assign default ROLE_USER
//         Role userRole = roleRepository
//             .findByName("ROLE_USER")
//             .orElseThrow(() -> new RuntimeException("Default role not found"));
//         user.setRoles(Collections.singleton(userRole));

//         userRepository.save(user);

//         return ResponseEntity
//             .status(HttpStatus.CREATED)
//             .body(new RegisterResponse("User registered successfully"));
//     }
// }

package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AuthRequest;
import com.suhasan.finance.account_service.dto.AuthResponse;
import com.suhasan.finance.account_service.dto.RegisterRequest;
import com.suhasan.finance.account_service.dto.RegisterResponse;
import com.suhasan.finance.account_service.entity.Role;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.repository.RoleRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import com.suhasan.finance.account_service.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest req) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                req.getUsername(), req.getPassword()
            )
        );
        String token = tokenProvider.generateToken(auth);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest req) {

        // 1) check for duplicates
        if (userRepository.existsByUsername(req.getUsername())) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new RegisterResponse(
                    "Username is already taken",
                    Collections.emptySet()
                ));
        }

        // 2) build the new user
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));

        // 3) assign default role
        Role userRole = roleRepository
            .findByName("ROLE_USER")
            .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.setRoles(Collections.singleton(userRole));

        // 4) save
        userRepository.save(user);

        // 5) return both message + assigned roles
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(new RegisterResponse(
                "User registered successfully",
                Collections.singleton(userRole.getName())
            ));
    }
}
