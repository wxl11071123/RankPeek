package io.rankpeek.service;

import io.rankpeek.model.LcuWindowBounds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LcuWindowBoundsServiceTest {

    @Test
    void findsLargestVisibleWindowBelongingToLeagueClientUx() {
        LcuWindowBoundsService service = new LcuWindowBoundsService(
                () -> Set.of(101, 202),
                () -> List.of(
                        new LcuWindowBoundsService.TopLevelWindow(1L, 101, 0, 0, 400, 300),
                        new LcuWindowBoundsService.TopLevelWindow(2L, 202, 50, 60, 1280, 720),
                        new LcuWindowBoundsService.TopLevelWindow(3L, 303, 10, 10, 1600, 900)
                )
        );

        LcuWindowBounds bounds = service.findLcuWindowBounds();

        assertThat(bounds.found()).isTrue();
        assertThat(bounds.x()).isEqualTo(50);
        assertThat(bounds.y()).isEqualTo(60);
        assertThat(bounds.width()).isEqualTo(1280);
        assertThat(bounds.height()).isEqualTo(720);
    }

    @Test
    void returnsNotFoundWhenNoLcuWindowExists() {
        LcuWindowBoundsService service = new LcuWindowBoundsService(
                () -> Set.of(101),
                () -> List.of(new LcuWindowBoundsService.TopLevelWindow(1L, 303, 10, 10, 1600, 900))
        );

        LcuWindowBounds bounds = service.findLcuWindowBounds();

        assertThat(bounds.found()).isFalse();
        assertThat(bounds.x()).isNull();
        assertThat(bounds.width()).isNull();
    }

    @Test
    void ignoresMinimizedPlaceholderBounds() {
        LcuWindowBoundsService service = new LcuWindowBoundsService(
                () -> Set.of(101),
                () -> List.of(new LcuWindowBoundsService.TopLevelWindow(1L, 101, -32000, -32000, 160, 28))
        );

        LcuWindowBounds bounds = service.findLcuWindowBounds();

        assertThat(bounds.found()).isFalse();
    }

    @Test
    void returnsNotFoundWhenNativeLookupFails() {
        LcuWindowBoundsService service = new LcuWindowBoundsService(
                () -> {
                    throw new RuntimeException("native error");
                },
                List::of
        );

        LcuWindowBounds bounds = service.findLcuWindowBounds();

        assertThat(bounds.found()).isFalse();
    }
}
