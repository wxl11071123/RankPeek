package io.rankpeek.server.auth;

public record UserResponse(Long id, String email, String displayName, String role, String status) {

    public static UserResponse from(AuthUser user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), user.role(), user.status());
    }
}
