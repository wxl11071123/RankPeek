package io.rankpeek.server.esports;

import io.rankpeek.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/esports/lpl")
public class LplEsportsController {

    private final LplEsportsService lplEsportsService;

    public LplEsportsController(LplEsportsService lplEsportsService) {
        this.lplEsportsService = lplEsportsService;
    }

    @GetMapping("/champions/{championId}")
    public ApiResponse<List<LplChampionUsage>> championUsage(
            @PathVariable Integer championId,
            @RequestParam String patchKey,
            @RequestParam String role
    ) {
        return ApiResponse.success(lplEsportsService.findChampionUsage(patchKey, championId, role));
    }
}
