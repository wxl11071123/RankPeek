package io.rankpeek.server.analysis;

import io.rankpeek.server.ai.AnalysisResult;
import io.rankpeek.server.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/pregame/mock")
    public ApiResponse<AnalysisResult> pregameMock(@RequestBody PregameAnalysisRequest request) {
        return ApiResponse.success(analysisService.generatePregameMock(request));
    }

    @PostMapping(value = "/pregame/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pregameStream(@RequestBody PregameAnalysisRequest request) {
        return analysisService.streamPregameMock(request);
    }
}
