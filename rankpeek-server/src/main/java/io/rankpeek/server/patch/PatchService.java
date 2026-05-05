package io.rankpeek.server.patch;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PatchService {

    private final PatchRepository patchRepository;

    public PatchService(PatchRepository patchRepository) {
        this.patchRepository = patchRepository;
    }

    public Optional<PatchVersion> findCurrentPatch() {
        return patchRepository.findCurrent();
    }

    public Optional<PatchVersion> findPatchVersion(String patchKey) {
        return patchRepository.findByPatchKey(patchKey);
    }

    public PatchVersion saveMockPatchVersion(String patchKey) {
        PatchVersion patchVersion = patchRepository.findByPatchKey(patchKey)
                .orElseGet(() -> patchRepository.insertMockPatch(patchKey));
        patchRepository.ensureMockChange(patchVersion);
        return patchVersion;
    }

    public List<PatchChange> findPatchChanges(String patchKey) {
        return patchRepository.findChanges(patchKey);
    }

    public boolean hasChampionChange(String patchKey, Integer championId) {
        return patchRepository.hasChampionChange(patchKey, championId);
    }
}
