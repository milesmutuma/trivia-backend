package com.mabawa.triviacrave.user.resources;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.user.service.UserService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

@DgsComponent
@RequiredArgsConstructor
public class UserDataFetcher {
    private final UserService userService;

    @DgsMutation
    @PreAuthorize("permitAll()") // Public - user registration
    public ApiResponse createUser(@InputArgument CreateUserCmd command) {
        return userService.createUser(command);
    }

    @DgsMutation  
    @PreAuthorize("permitAll()") // Public - user login
    public ApiResponse loginUser(@InputArgument LoginCmd command) {
        return userService.loginUser(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('USER')") // Requires authentication
    public ApiResponse changePassword(@InputArgument ChangePasswordCmd command) {
        return userService.changePassword(command);
    }

    @DgsMutation
    @PreAuthorize("permitAll()") // Public - forgot password
    public ApiResponse forgotPassword(@InputArgument ForgotPasswordCmd command) {
        return userService.forgotPassword(command);
    }

    @DgsMutation
    @PreAuthorize("permitAll()") // Public - reset password
    public ApiResponse resetPassword(@InputArgument ResetPasswordCmd command) {
        return userService.resetPassword(command);
    }
}
