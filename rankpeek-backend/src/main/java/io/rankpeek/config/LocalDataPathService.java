package io.rankpeek.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves RankPeek local data paths without making application startup depend on disk availability.
 */
@Slf4j
@Service
public class LocalDataPathService {

    public Path getCacheDatabasePath() {
        Path cacheDirectory = getCacheDirectory();
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            log.warn("Failed to create local cache directory: {}", cacheDirectory, e);
        }
        return cacheDirectory.resolve("rankpeek-cache");
    }

    private Path getCacheDirectory() {
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "RankPeek", "cache");
            }
        }
        return Path.of(System.getProperty("user.home", "."), ".rankpeek", "cache");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
