package io.rankpeek.server.analysis;

import io.rankpeek.server.auth.AuthService;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analysis/runs")
public class AdminAnalysisRunsController {

    private final AnalysisService analysisService;
    private final AuthService authService;

    public AdminAnalysisRunsController(AnalysisService analysisService, AuthService authService) {
        this.analysisService = analysisService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<AdminAnalysisRunListResponse> listRuns(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(analysisService.listAdminRuns(userId, endpoint, status, limit, offset));
    }

    @GetMapping("/{runId}")
    public ApiResponse<AdminAnalysisRunSummaryResponse> getRun(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long runId
    ) {
        authService.requireAdmin(authorizationHeader);
        return ApiResponse.success(analysisService.getAdminRun(runId));
    }
}
