package com.mabawa.triviacrave.user.service.impl;

import com.mabawa.triviacrave.common.security.JwtAuthenticationToken;
import com.mabawa.triviacrave.common.security.JwtTokenService;
import com.mabawa.triviacrave.common.utils.IDGenerator;
import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.user.entity.PasswordResetToken;
import com.mabawa.triviacrave.user.entity.User;
import com.mabawa.triviacrave.user.repository.PasswordResetTokenRepository;
import com.mabawa.triviacrave.user.repository.UserRepository;
import com.mabawa.triviacrave.user.service.EmailService;
import com.mabawa.triviacrave.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public ApiResponse createUser(CreateUserCmd cmd) {
        userRepository.findByEmail(cmd.getEmail()).ifPresent(u -> {
            throw new IllegalArgumentException("Email already in use");
        });

        Long id = IDGenerator.generateId();
        User user = User.builder()
                .id(id)
                .displayName(cmd.getDisplayName())
                .email(cmd.getEmail())
                .phoneNumber(null) // Optional field, can be set later
                .password(passwordEncoder.encode(cmd.getPassword()))
                .role(User.Role.ROLE_USER)
                .createdAt(LocalDateTime.now())
                .enabled(true)
                .build();

        userRepository.save(user);

        emailService.sendWelcomeEmail(user.getEmail(), user.getDisplayName());

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("role", user.getRole().name());
        String token = jwtTokenService.generateToken(claims, user.getEmail());

        return ApiResponse.newBuilder()
                .status(200)
                .message("User created successfully")
                .data(LoggedInUser.newBuilder()
                        .users(mapToGraphQLUser(user))
                        .token(token)
                        .build())
                .build();
    }

    @Override
    public ApiResponse loginUser(LoginCmd cmd) {
        User user = userRepository.findByEmail(cmd.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(cmd.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("role", user.getRole().name());
        String token = jwtTokenService.generateToken(claims, user.getEmail());

        return ApiResponse.newBuilder()
                .status(200)
                .message("Login successful")
                .data(LoggedInUser.newBuilder()
                        .users(mapToGraphQLUser(user))
                        .token(token)
                        .build())
                .build();
    }

    @Override
    public ApiResponse changePassword(Long userId, ChangePasswordCmd cmd) {
        User user = userRepository.findOne(userId);
        if (!passwordEncoder.matches(cmd.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password does not match");
        }
        user.setPassword(passwordEncoder.encode(cmd.getNewPassword()));
        userRepository.save(user);
        return ApiResponse.newBuilder()
                .status(200)
                .message("Password changed successfully")
                .data(com.mabawa.triviacrave.generated.graphql.types.Empty.newBuilder().ok(true).build())
                .build();
    }

    @Override
    public ApiResponse changePassword(ChangePasswordCmd cmd) {
        // Extract authenticated user ID from security context
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) SecurityContextHolder
                .getContext()
                .getAuthentication();
        Long userId = authentication.getUserId();
        return changePassword(userId, cmd);
    }

    @Override
    @Transactional
    public ApiResponse forgotPassword(ForgotPasswordCmd cmd) {
        Optional<User> userOpt = userRepository.findByEmail(cmd.getEmail());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            passwordResetTokenRepository.markAllUserTokensAsUsed(user);
            
            Long tokenId = IDGenerator.generateId();
            PasswordResetToken resetToken = PasswordResetToken.createToken(tokenId, user);
            passwordResetTokenRepository.save(resetToken);
            
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
        }
        
        return ApiResponse.newBuilder()
                .status(200)
                .message("If the email exists, a password reset link has been sent")
                .data(com.mabawa.triviacrave.generated.graphql.types.Empty.newBuilder().ok(true).build())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse resetPassword(ResetPasswordCmd cmd) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(cmd.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        if (cmd.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(cmd.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return ApiResponse.newBuilder()
                .status(200)
                .message("Password reset successfully")
                .data(com.mabawa.triviacrave.generated.graphql.types.Empty.newBuilder().ok(true).build())
                .build();
    }

    @Override
    @Cacheable(value = "users", key = "#userId")
    public User getUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findOne(userId);
    }

    @Override
    @Cacheable(value = "users", key = "#email")
    public User getUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    public boolean userExists(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findOne(userId) != null;
    }

    private com.mabawa.triviacrave.generated.graphql.types.User mapToGraphQLUser(User user) {
        return com.mabawa.triviacrave.generated.graphql.types.User.newBuilder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(com.mabawa.triviacrave.generated.graphql.types.Role.valueOf(user.getRole().name()))
                .build();
    }
}
