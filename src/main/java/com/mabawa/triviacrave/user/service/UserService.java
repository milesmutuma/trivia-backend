package com.mabawa.triviacrave.user.service;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.user.entity.User;

public interface UserService {
    // Authentication and user management
    ApiResponse createUser(CreateUserCmd cmd);
    ApiResponse loginUser(LoginCmd cmd);
    default ApiResponse loginUser(CreateUserCmd cmd) {
        return loginUser(LoginCmd.newBuilder().email(cmd.getEmail()).password(cmd.getPassword()).build());
    }
    ApiResponse changePassword(Long userId, ChangePasswordCmd cmd);
    ApiResponse changePassword(ChangePasswordCmd cmd);
    ApiResponse forgotPassword(ForgotPasswordCmd cmd);
    ApiResponse resetPassword(ResetPasswordCmd cmd);
    
    // Domain service methods for other services
    User getUserById(Long userId);
    User getUserByEmail(String email);
    boolean userExists(Long userId);
}
