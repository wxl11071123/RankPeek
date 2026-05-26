package io.rankpeek.server.credits;

import io.rankpeek.server.auth.AuthService;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/credits")
public class AdminCreditsController {

    private static final String IDEMPOTENCY_HEADER = "X-RankPeek-Idempotency-Key";

    private final AuthService authService;
    private final CreditService creditService;

    public AdminCreditsController(AuthService authService, CreditService creditService) {
        this.authService = authService;
        this.creditService = creditService;
    }

    @PostMapping("/grants")
    public ApiResponse<AdminCreditGrantResponse> grant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AdminCreditGrantRequest request
    ) {
        AuthUser admin = authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(creditService.adjustByAdmin(admin, request, idempotencyKey));
    }
}
