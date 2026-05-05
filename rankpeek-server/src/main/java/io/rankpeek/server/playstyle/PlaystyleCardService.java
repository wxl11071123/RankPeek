package io.rankpeek.server.playstyle;

import io.rankpeek.server.patch.PatchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaystyleCardService {

    private final PlaystyleCardRepository playstyleCardRepository;
    private final PatchService patchService;

    public PlaystyleCardService(PlaystyleCardRepository playstyleCardRepository, PatchService patchService) {
        this.playstyleCardRepository = playstyleCardRepository;
        this.patchService = patchService;
    }

    public PlaystyleCard createMockCard(String patchKey, Integer championId, String role) {
        return playstyleCardRepository.createMockCard(patchKey, championId, role);
    }

    public List<PlaystyleCard> findCards(String patchKey, Integer championId, String role) {
        return playstyleCardRepository.findStoredCards(patchKey, championId, role)
                .stream()
                .map(this::applyFreshnessRules)
                .toList();
    }

    public void addInvalidatingRule(String patchKey, Integer championId, String note) {
        playstyleCardRepository.addInvalidatingRule(patchKey, championId, note);
    }

    private PlaystyleCard applyFreshnessRules(PlaystyleCard card) {
        if (playstyleCardRepository.hasInvalidatingRule(card.patchKey(), card.championId())) {
            return card.withFreshnessStatus("EXPIRED");
        }
        if (patchService.hasChampionChange(card.patchKey(), card.championId())) {
            return card.withFreshnessStatus("STALE");
        }
        return card;
    }
}
