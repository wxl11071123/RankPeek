package io.rankpeek.server.auth;

import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUsersController {

    private final AuthService authService;

    public AdminUsersController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<AdminUserListResponse> listUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        AuthUser admin = authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(authService.listUsers(admin, query, status, role, limit, offset));
    }

    @PatchMapping("/{userId}")
    public ApiResponse<AdminUserResponse> updateUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long userId,
            @RequestBody AdminUserUpdateRequest request
    ) {
        AuthUser admin = authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(authService.updateUserByAdmin(admin, userId, request));
    }

    @PostMapping("/{userId}/sessions/revoke")
    public ApiResponse<AdminUserSessionRevokeResponse> revokeSessions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long userId
    ) {
        AuthUser admin = authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(authService.revokeUserSessionsByAdmin(admin, userId));
    }
}
