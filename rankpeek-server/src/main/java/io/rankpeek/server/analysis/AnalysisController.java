package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.AnalysisResult;
import io.rankpeek.server.auth.AuthService;
import io.rankpeek.server.auth.AuthUser;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private static final String IDEMPOTENCY_HEADER = "X-RankPeek-Idempotency-Key";

    private final AnalysisService analysisService;
    private final AuthService authService;

    public AnalysisController(AnalysisService analysisService, AuthService authService) {
        this.analysisService = analysisService;
        this.authService = authService;
    }

    @PostMapping("/pregame/mock")
    public ApiResponse<AnalysisResult> pregameMock(@RequestBody PregameAnalysisRequest request) {
        return ApiResponse.success(analysisService.generatePregameMock(request));
    }

    @PostMapping(value = "/pregame/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pregameStream(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody PregameAnalysisRequest request
    ) {
        AuthUser user = analysisService.deepSeekEnabled() ? authService.requireCurrentUser(authorizationHeader) : null;
        return analysisService.streamPregame(request, user);
    }

    @PostMapping(value = "/postgame/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter postgameStream(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody PostgameAnalysisRequest request
    ) {
        AuthUser user = analysisService.deepSeekEnabled() ? authService.requireCurrentUser(authorizationHeader) : null;
        return analysisService.streamPostgame(request, user);
    }

    @PostMapping("/coach-summary")
    public ApiResponse<CoachSummaryAnalysisResponse> coachSummary(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody CoachSummaryAnalysisRequest request
    ) {
        AuthUser user = authService.requireCurrentUser(authorizationHeader);
        return analysisService.generateCoachSummary(request, user, idempotencyKey);
    }

    @GetMapping("/runs")
    public ApiResponse<AnalysisRunListResponse> listRuns(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        AuthUser user = authService.requireCurrentUser(authorizationHeader);
        return ApiResponse.success(analysisService.listUserRuns(user, endpoint, status, limit, offset));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<AnalysisRunDetailResponse> getRun(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long runId
    ) {
        AuthUser user = authService.requireCurrentUser(authorizationHeader);
        return ApiResponse.success(analysisService.getUserRun(user, runId));
    }
}
