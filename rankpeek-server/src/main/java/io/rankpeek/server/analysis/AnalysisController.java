package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.AnalysisResult;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/pregame/mock")
    public ApiResponse<AnalysisResult> pregameMock(@RequestBody PregameAnalysisRequest request) {
        return ApiResponse.success(analysisService.generatePregameMock(request));
    }
}
