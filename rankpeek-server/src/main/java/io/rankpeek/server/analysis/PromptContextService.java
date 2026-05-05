package io.rankpeek.server.analysis;

import io.rankpeek.server.cnmeta.CnMetaService;
import io.rankpeek.server.esports.LplEsportsService;
import io.rankpeek.server.patch.PatchService;
import io.rankpeek.server.playstyle.PlaystyleCardService;
import org.springframework.stereotype.Service;

@Service
public class PromptContextService {

    private final PatchService patchService;
    private final CnMetaService cnMetaService;
    private final LplEsportsService lplEsportsService;
    private final PlaystyleCardService playstyleCardService;

    public PromptContextService(
            PatchService patchService,
            CnMetaService cnMetaService,
            LplEsportsService lplEsportsService,
            PlaystyleCardService playstyleCardService
    ) {
        this.patchService = patchService;
        this.cnMetaService = cnMetaService;
        this.lplEsportsService = lplEsportsService;
        this.playstyleCardService = playstyleCardService;
    }

    public PromptContext buildContext(Integer championId, String role, String patchKey, Integer queueId) {
        var patchNotes = patchService.findPatchChanges(patchKey)
                .stream()
                .map(change -> change.summaryEn() == null ? change.changeType() : change.summaryEn())
                .sorted()
                .toList();
        var cnMetaNotes = cnMetaService.findChampionMeta(patchKey, championId, role, "PLATINUM_PLUS")
                .stream()
                .map(meta -> "CN " + meta.source() + " " + meta.role()
                        + " winRate=" + meta.winRate()
                        + " pickRate=" + meta.pickRate())
                .sorted()
                .toList();
        var lplNotes = lplEsportsService.findChampionUsage(patchKey, championId, role)
                .stream()
                .map(usage -> "LPL " + usage.playerName() + " " + usage.team()
                        + " KDA=" + usage.kills() + "/" + usage.deaths() + "/" + usage.assists())
                .sorted()
                .toList();
        var cards = playstyleCardService.findCards(patchKey, championId, role);

        return new PromptContext(patchKey, championId, role, queueId, patchNotes, cnMetaNotes, lplNotes, cards);
    }
}
