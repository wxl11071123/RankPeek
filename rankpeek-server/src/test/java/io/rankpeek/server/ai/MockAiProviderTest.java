package io.rankpeek.server.ai;

import io.rankpeek.server.analysis.AnalysisPrompt;
import io.rankpeek.server.analysis.PromptContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    @Test
    void returnsDeterministicMockResponseWithoutApiKey() {
        MockAiProvider provider = new MockAiProvider();
        PromptContext context = new PromptContext(
                "26.09",
                81,
                "ADC",
                420,
                List.of("patch-note"),
                List.of("cn-meta"),
                List.of("lpl-note"),
                List.of()
        );
        AnalysisPrompt prompt = new AnalysisPrompt(context, List.of("has_frontline"), List.of("many_divers"));

        AnalysisResult first = provider.generateAnalysis(prompt);
        AnalysisResult second = provider.generateAnalysis(prompt);

        assertThat(provider.requiresApiKey()).isFalse();
        assertThat(second).isEqualTo(first);
        assertThat(first.cost().mock()).isTrue();
        assertThat(first.cost().chargedCredits()).isZero();
    }
}
