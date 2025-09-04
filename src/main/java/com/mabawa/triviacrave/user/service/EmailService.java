package com.mabawa.triviacrave.user.service;

public interface EmailService {
    void sendPasswordResetEmail(String email, String token);
    void sendWelcomeEmail(String email, String displayName);
    boolean isEmailConfigured();
}