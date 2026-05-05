package io.rankpeek.server.esports;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LplEsportsService {

    private final LplEsportsRepository lplEsportsRepository;

    public LplEsportsService(LplEsportsRepository lplEsportsRepository) {
        this.lplEsportsRepository = lplEsportsRepository;
    }

    public LplChampionUsage saveMockUsage(String patchKey, Integer championId, String role) {
        return lplEsportsRepository.saveMockUsage(patchKey, championId, role);
    }

    public List<LplChampionUsage> findChampionUsage(String patchKey, Integer championId, String role) {
        return lplEsportsRepository.findChampionUsage(patchKey, championId, role);
    }

    public boolean isExternalNetworkDisabled() {
        return true;
    }
}
