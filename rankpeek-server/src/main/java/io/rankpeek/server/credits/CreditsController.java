package io.rankpeek.server.credits;

import io.rankpeek.server.auth.AuthService;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credits")
@CrossOrigin(origins = "*")
public class CreditsController {

    private final AuthService authService;
    private final CreditService creditService;

    public CreditsController(AuthService authService, CreditService creditService) {
        this.authService = authService;
        this.creditService = creditService;
    }

    @GetMapping("/balance")
    public ApiResponse<CreditBalanceResponse> balance(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        AuthUser user = authService.requireCurrentUser(authorizationHeader);
        return ApiResponse.success(creditService.balanceFor(user));
    }

    @GetMapping("/ledger")
    public ApiResponse<CreditLedgerResponse> ledger(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        AuthUser user = authService.requireCurrentUser(authorizationHeader);
        return ApiResponse.success(creditService.ledgerFor(user));
    }
}
