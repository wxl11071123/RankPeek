package io.rankpeek.server.cnmeta;

import io.rankpeek.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cn-meta")
public class CnMetaController {

    private final CnMetaService cnMetaService;

    public CnMetaController(CnMetaService cnMetaService) {
        this.cnMetaService = cnMetaService;
    }

    @GetMapping("/champions/{championId}")
    public ApiResponse<List<CnChampionMeta>> championMeta(
            @PathVariable Integer championId,
            @RequestParam String patchKey,
            @RequestParam String role,
            @RequestParam(defaultValue = "PLATINUM_PLUS") String tierScope
    ) {
        return ApiResponse.success(cnMetaService.findChampionMeta(patchKey, championId, role, tierScope));
    }
}
