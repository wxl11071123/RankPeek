package io.rankpeek.server.analysis;

import io.rankpeek.server.playstyle.PlaystyleCard;

import java.util.List;

public record PromptContext(
        String patchKey,
        Integer championId,
        String role,
        Integer queueId,
        List<String> patchNotes,
        List<String> cnMetaNotes,
        List<String> lplNotes,
        List<PlaystyleCard> playstyleCards
) {
}
