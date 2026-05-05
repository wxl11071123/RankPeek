package io.rankpeek.server.playstyle;

import io.rankpeek.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/playstyles/cards")
public class PlaystyleCardController {

    private final PlaystyleCardService playstyleCardService;

    public PlaystyleCardController(PlaystyleCardService playstyleCardService) {
        this.playstyleCardService = playstyleCardService;
    }

    @GetMapping
    public ApiResponse<List<PlaystyleCard>> cards(
            @RequestParam String patchKey,
            @RequestParam Integer championId,
            @RequestParam String role
    ) {
        return ApiResponse.success(playstyleCardService.findCards(patchKey, championId, role));
    }

    @PostMapping("/mock-seed")
    public ApiResponse<PlaystyleCard> mockSeed(
            @RequestParam(defaultValue = "26.09") String patchKey,
            @RequestParam(defaultValue = "81") Integer championId,
            @RequestParam(defaultValue = "ADC") String role
    ) {
        return ApiResponse.success(playstyleCardService.createMockCard(patchKey, championId, role));
    }
}
