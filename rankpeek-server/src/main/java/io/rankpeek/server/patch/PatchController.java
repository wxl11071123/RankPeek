package io.rankpeek.server.patch;

import io.rankpeek.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patch")
public class PatchController {

    private final PatchService patchService;

    public PatchController(PatchService patchService) {
        this.patchService = patchService;
    }

    @GetMapping("/current")
    public ApiResponse<PatchVersion> currentPatch() {
        PatchVersion patchVersion = patchService.findCurrentPatch()
                .orElseGet(() -> patchService.saveMockPatchVersion("26.09"));
        return ApiResponse.success(patchVersion);
    }

    @GetMapping("/{patchKey}/changes")
    public ApiResponse<List<PatchChange>> patchChanges(@PathVariable String patchKey) {
        return ApiResponse.success(patchService.findPatchChanges(patchKey));
    }
}
