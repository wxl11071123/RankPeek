package io.rankpeek.server.admin;

import io.rankpeek.server.auth.AuthService;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;
    private final AuthService authService;

    public AdminStatsController(AdminStatsService statsService, AuthService authService) {
        this.statsService = statsService;
        this.authService = authService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminStatsOverviewResponse> overview(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String zone
    ) {
        authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(statsService.overview(date, zone));
    }
}
