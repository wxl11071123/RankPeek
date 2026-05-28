package io.rankpeek.server.auth;

import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return ApiResponse.success(authService.register(request, userAgent));
    }

    @PostMapping("/register/email-code")
    public ApiResponse<EmailVerificationResponse> requestRegisterEmailCode(
            @RequestBody EmailVerificationRequest request
    ) {
        return ApiResponse.success(authService.requestRegisterEmailCode(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return ApiResponse.success(authService.login(request, userAgent));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.logout(request));
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<PasswordResetRequestResponse> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        return ApiResponse.success(authService.requestPasswordReset(request));
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<PasswordResetConfirmResponse> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        return ApiResponse.success(authService.confirmPasswordReset(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ApiResponse.success(authService.currentUser(authorizationHeader));
    }
}
