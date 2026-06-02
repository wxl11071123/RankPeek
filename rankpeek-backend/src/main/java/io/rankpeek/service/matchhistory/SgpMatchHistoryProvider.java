package io.rankpeek.service.matchhistory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.MatchTimeline;
import io.rankpeek.model.MatchTimelineFetchResult;
import io.rankpeek.sgp.SgpAuthState;
import io.rankpeek.sgp.SgpGameDetailMapper;
import io.rankpeek.sgp.SgpHttpClient;
import io.rankpeek.sgp.SgpMatchHistoryMapper;
import io.rankpeek.sgp.SgpServerResolver;
import io.rankpeek.sgp.SgpStatus;
import io.rankpeek.sgp.SgpTimelineMapper;
import io.rankpeek.sgp.SgpTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SgpMatchHistoryProvider implements MatchHistoryProvider {

    private static final int DEFAULT_START_INDEX = 0;
    private static final int DEFAULT_END_INDEX = 99;
    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int MAX_SUMMARY_COUNT = 200;

    private final SgpHttpClient sgpHttpClient;
    private final SgpMatchHistoryMapper matchHistoryMapper;
    private final SgpGameDetailMapper gameDetailMapper;
    private final SgpTimelineMapper timelineMapper;
    private final ObjectMapper objectMapper;
    private final SgpServerResolver serverResolver;
    private final SgpTokenService tokenService;

    @Override
    public MatchHistorySource source() {
        return MatchHistorySource.SGP;
    }

    @Override
    public MatchHistoryFetchResult fetchMatchHistory(String puuid, MatchHistoryQueryOptions options) {
        MatchHistoryQueryOptions queryOptions = normalizeOptions(options);
        int startIndex = Math.max(DEFAULT_START_INDEX, queryOptions.begIndex());
        int endIndex = queryOptions.endIndex() >= startIndex ? queryOptions.endIndex() : DEFAULT_END_INDEX;
        int maxResults = queryOptions.maxResults() != null && queryOptions.maxResults() > 0
                ? queryOptions.maxResults()
                : DEFAULT_MAX_RESULTS;
        int count = Math.max(1, Math.min(MAX_SUMMARY_COUNT, Math.min(maxResults, endIndex - startIndex + 1)));

        JsonNode response = sgpHttpClient.getMatchHistorySummary(
                puuid,
                startIndex,
                count,
                queryOptions.tag(),
                resolveSgpServerId(queryOptions)
        );
        List<MatchHistory> matches = matchHistoryMapper.mapMatchHistorySummary(response);
        attachRequestedPuuidToSingleParticipantSummaries(matches, puuid);
        return MatchHistoryFetchResult.builder()
                .matches(matches)
                .rawSummaryJsonByGameId(matchHistoryMapper.rawSummaryJsonByGameId(response))
                .rawEmpty(matches == null || matches.isEmpty())
                .build();
    }

    @Override
    public GameDetail fetchGameDetail(Long gameId, MatchHistoryQueryOptions options) {
        JsonNode response = sgpHttpClient.getGameDetails(gameId, resolveSgpServerId(normalizeOptions(options)));
        return gameDetailMapper.mapGameDetails(response);
    }

    @Override
    public MatchTimelineFetchResult fetchGameTimeline(Long gameId, MatchHistoryQueryOptions options) {
        JsonNode response = sgpHttpClient.getGameDetails(gameId, resolveSgpServerId(normalizeOptions(options)));
        MatchTimeline timeline = timelineMapper.mapTimeline(response);
        String rawJson = writeJson(response);
        String status = timelineMapper.hasTimeline(response) ? "FETCHED" : "EMPTY";
        return MatchTimelineFetchResult.builder()
                .gameId(timeline.getGameId() == null ? gameId : timeline.getGameId())
                .timeline(timeline)
                .rawDetailJson(rawJson)
                .rawTimelineJson(rawJson)
                .status(status)
                .build();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ignored) {
            return node == null ? null : node.toString();
        }
    }

    private void attachRequestedPuuidToSingleParticipantSummaries(List<MatchHistory> matches, String puuid) {
        if (!hasText(puuid) || matches == null || matches.isEmpty()) {
            return;
        }

        for (MatchHistory match : matches) {
            if (match == null || match.getParticipants() == null || match.getParticipants().size() != 1) {
                continue;
            }
            if (hasIdentityForPuuid(match, puuid)) {
                continue;
            }

            MatchHistory.Participant participant = match.getParticipants().getFirst();
            if (participant == null || participant.getParticipantId() == null) {
                continue;
            }
            MatchHistory.ParticipantIdentity identity = findIdentityByParticipantId(match, participant.getParticipantId());
            if (identity == null) {
                identity = new MatchHistory.ParticipantIdentity();
                identity.setParticipantId(participant.getParticipantId());
                List<MatchHistory.ParticipantIdentity> identities = match.getParticipantIdentities() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(match.getParticipantIdentities());
                identities.add(identity);
                match.setParticipantIdentities(identities);
            }
            if (identity.getPlayer() == null) {
                identity.setPlayer(new MatchHistory.Player());
            }
            if (!hasText(identity.getPlayer().getPuuid())) {
                identity.getPlayer().setPuuid(puuid.trim());
            }
        }
    }

    private boolean hasIdentityForPuuid(MatchHistory match, String puuid) {
        if (match.getParticipantIdentities() == null) {
            return false;
        }
        return match.getParticipantIdentities().stream()
                .anyMatch(identity -> identity != null
                        && identity.getPlayer() != null
                        && puuid.equals(identity.getPlayer().getPuuid()));
    }

    private MatchHistory.ParticipantIdentity findIdentityByParticipantId(MatchHistory match, Integer participantId) {
        if (participantId == null || match.getParticipantIdentities() == null) {
            return null;
        }
        return match.getParticipantIdentities().stream()
                .filter(identity -> identity != null && participantId.equals(identity.getParticipantId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean supports(MatchHistoryQueryOptions options) {
        MatchHistoryQueryOptions queryOptions = normalizeOptions(options);
        if (queryOptions.preferredSource() == MatchHistorySource.LCU
                || queryOptions.preferredSource() == MatchHistorySource.CACHE) {
            return false;
        }
        return isReady(resolveStatus(queryOptions));
    }

    private MatchHistoryQueryOptions normalizeOptions(MatchHistoryQueryOptions options) {
        return options == null ? MatchHistoryQueryOptions.defaultFor(MatchHistorySource.SGP, false) : options;
    }

    private String resolveSgpServerId(MatchHistoryQueryOptions options) {
        if (hasText(options.sgpServerId())) {
            return options.sgpServerId().trim();
        }

        SgpStatus status = serverResolver.resolveCurrentStatus();
        if (!isReady(status)) {
            String message = status != null && hasText(status.getMessage())
                    ? status.getMessage()
                    : "SGP match-history is not ready";
            throw new IllegalStateException(message);
        }

        String sgpServerId = firstText(status.getSgpServerId(), status.getPlatformId());
        if (!hasText(sgpServerId)) {
            throw new IllegalStateException("SGP server id is missing");
        }
        return sgpServerId;
    }

    private SgpStatus resolveStatus(MatchHistoryQueryOptions options) {
        if (hasText(options.sgpServerId())) {
            return serverResolver.resolveStatus(options.sgpServerId());
        }
        return serverResolver.resolveCurrentStatus();
    }

    private boolean isReady(SgpStatus status) {
        return status != null
                && status.isSupported()
                && status.isMatchHistorySupported()
                && tokenReady(status)
                && hasText(firstText(status.getSgpServerId(), status.getPlatformId()));
    }

    private boolean tokenReady(SgpStatus status) {
        if (status.isTokenReady()) {
            return true;
        }
        SgpAuthState statusAuthState = status.getAuthState();
        if (statusAuthState != null && statusAuthState.isReady()) {
            return true;
        }
        SgpAuthState authState = tokenService.getAuthState();
        return authState != null && authState.isReady();
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
