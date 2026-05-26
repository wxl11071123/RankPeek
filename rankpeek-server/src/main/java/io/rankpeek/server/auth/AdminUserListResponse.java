package io.rankpeek.server.auth;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserResponse> users,
        long total,
        int limit,
        int offset
) {
}
