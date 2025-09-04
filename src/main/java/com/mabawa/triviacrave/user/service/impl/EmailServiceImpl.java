package com.mabawa.triviacrave.user.service.impl;

import com.mabawa.triviacrave.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.name:TriviaCrave}")
    private String appName;

    @Override
    public void sendPasswordResetEmail(String email, String token) {
        if (!emailEnabled) {
            log.info("Email disabled. Password reset for: {}, Reset link: {}/reset-password?token={}", 
                    email, frontendUrl, token);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("Password Reset Request - " + appName);
            helper.setText(createPasswordResetEmailContent(email, token), true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    @Override
    public void sendWelcomeEmail(String email, String displayName) {
        if (!emailEnabled) {
            log.info("Email disabled. Welcome email would be sent to: {} ({})", email, displayName);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("Welcome to " + appName + "!");
            helper.setText(createWelcomeEmailContent(displayName), true);

            mailSender.send(message);
            log.info("Welcome email sent to: {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to: {}", email, e);
        }
    }

    @Override
    public boolean isEmailConfigured() {
        return emailEnabled && fromEmail != null && !fromEmail.isEmpty();
    }

    private String createPasswordResetEmailContent(String email, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f8f9fa; padding: 30px; border-radius: 10px; text-align: center;">
                    <h1 style="color: #2c3e50; margin-bottom: 30px;">%s</h1>
                    
                    <h2 style="color: #34495e; margin-bottom: 20px;">Password Reset Request</h2>
                    
                    <p style="font-size: 16px; margin-bottom: 30px;">
                        Hello,<br><br>
                        We received a request to reset your password for your %s account (%s).
                    </p>
                    
                    <div style="margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #007bff; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-size: 16px; display: inline-block;">
                            Reset Your Password
                        </a>
                    </div>
                    
                    <p style="font-size: 14px; color: #666; margin-top: 30px;">
                        If the button doesn't work, copy and paste this link into your browser:<br>
                        <a href="%s" style="color: #007bff; word-break: break-all;">%s</a>
                    </p>
                    
                    <div style="background-color: #fff3cd; padding: 15px; border-radius: 5px; margin: 30px 0; border-left: 4px solid #ffc107;">
                        <p style="margin: 0; font-size: 14px; color: #856404;">
                            <strong>Important:</strong> This link will expire in 24 hours and can only be used once. 
                            If you didn't request this password reset, please ignore this email.
                        </p>
                    </div>
                    
                    <p style="font-size: 12px; color: #666; margin-top: 40px;">
                        This is an automated message. Please do not reply to this email.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(appName, appName, email, resetLink, resetLink, resetLink);
    }

    private String createWelcomeEmailContent(String displayName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Welcome to %s</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f8f9fa; padding: 30px; border-radius: 10px; text-align: center;">
                    <h1 style="color: #2c3e50; margin-bottom: 30px;">%s</h1>
                    
                    <h2 style="color: #28a745; margin-bottom: 20px;">Welcome Aboard!</h2>
                    
                    <p style="font-size: 16px; margin-bottom: 30px;">
                        Hello %s,<br><br>
                        Welcome to %s! Your account has been successfully created.
                    </p>
                    
                    <p style="font-size: 16px; margin-bottom: 30px;">
                        You're now ready to start enjoying our trivia games and challenges. 
                        We're excited to have you as part of our community!
                    </p>
                    
                    <div style="margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #28a745; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-size: 16px; display: inline-block;">
                            Start Playing
                        </a>
                    </div>
                    
                    <p style="font-size: 14px; color: #666; margin-top: 30px;">
                        If you have any questions or need help getting started, feel free to contact our support team.
                    </p>
                    
                    <p style="font-size: 12px; color: #666; margin-top: 40px;">
                        This is an automated message. Please do not reply to this email.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(appName, appName, displayName, appName, frontendUrl);
    }
}