package io.rankpeek.server.cnmeta;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CnMetaService {

    private final CnMetaRepository cnMetaRepository;

    public CnMetaService(CnMetaRepository cnMetaRepository) {
        this.cnMetaRepository = cnMetaRepository;
    }

    public CnChampionMeta saveMockSnapshot(String patchKey, Integer championId, String role) {
        return cnMetaRepository.saveMockSnapshot(patchKey, championId, role);
    }

    public List<CnChampionMeta> findChampionMeta(String patchKey, Integer championId, String role, String tierScope) {
        return cnMetaRepository.findChampionMeta(patchKey, championId, role, tierScope);
    }

    public List<CnChampionMeta> findLatestChampionMeta(Integer championId, String tierScope) {
        return cnMetaRepository.findLatestChampionMeta(championId, tierScope);
    }

    public boolean isExternalNetworkDisabled() {
        return true;
    }
}
