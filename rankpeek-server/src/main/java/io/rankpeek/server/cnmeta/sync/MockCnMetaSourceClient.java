package io.rankpeek.server.cnmeta.sync;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class MockCnMetaSourceClient implements CnMetaSourceClient {

    @Override
    public String source() {
        return "mock-101";
    }

    @Override
    public CnMetaSourcePayload fetchChampionStats(String patchKey, Integer queueId, String tierScope, String role) {
        List<CnMetaChampionStatRow> rows = List.of(
                row(103, role, tierScope, "0.5210", "0.1430", "0.0610", "3.42", "11890.00", "0.2860", "0.1840", 1),
                row(81, role, tierScope, "0.5075", "0.1710", "0.0790", "3.18", "12110.00", "0.2740", "0.1910", 2),
                row(64, role, tierScope, "0.4985", "0.1020", "0.1320", "3.05", "10950.00", "0.2310", "0.2470", 3)
        );
        String requestKey = "%s|%d|%s|%s".formatted(patchKey, queueId, tierScope, role);
        String rawContent = """
                source=mock-101
                request=%s
                rows=103:0.5210:0.1430:0.0610;81:0.5075:0.1710:0.0790;64:0.4985:0.1020:0.1320
                """.formatted(requestKey).trim();
        return new CnMetaSourcePayload(
                source(),
                "mock://101-meta/%s/%d/%s/%s".formatted(patchKey, queueId, tierScope, role),
                requestKey,
                200,
                rawContent,
                LocalDate.of(2026, 5, 15),
                rows
        );
    }

    private static CnMetaChampionStatRow row(
            Integer championId,
            String role,
            String tierScope,
            String winRate,
            String pickRate,
            String banRate,
            String avgKda,
            String avgGold,
            String avgDamageShare,
            String avgDamageTakenShare,
            Integer rankIndex
    ) {
        return new CnMetaChampionStatRow(
                championId,
                role,
                tierScope,
                new BigDecimal(winRate),
                new BigDecimal(pickRate),
                new BigDecimal(banRate),
                new BigDecimal(avgKda),
                new BigDecimal(avgGold),
                new BigDecimal(avgDamageShare),
                new BigDecimal(avgDamageTakenShare),
                rankIndex,
                "deterministic mock 101 public aggregate"
        );
    }
}
