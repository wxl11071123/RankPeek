package io.rankpeek.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiProviderControllerTest {

    private AiProviderSettingsService service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = mock(AiProviderSettingsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiProviderController(service)).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void providers_returnsDeepSeekAndCustomOpenAiCompatibleProfiles() throws Exception {
        when(service.listProviderProfiles()).thenReturn(AiProviderSettingsService.defaultProviderProfiles());

        mockMvc.perform(get("/api/v1/ai/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.providers[0].id").value("deepseek"))
                .andExpect(jsonPath("$.data.providers[0].dialect").value("openai-compatible"))
                .andExpect(jsonPath("$.data.providers[0].defaultBaseUrl").value("https://api.deepseek.com"))
                .andExpect(jsonPath("$.data.providers[0].models[0]").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.data.providers[1].id").value("custom-openai-compatible"));
    }

    @Test
    void settings_returnsMaskedSettingsWithoutRawApiKey() throws Exception {
        when(service.getSettings()).thenReturn(new AiProviderSettings(
                true,
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                true,
                "sk-...abcd",
                0.4d,
                4096,
                new AiProviderPricing(
                        "CNY",
                        new BigDecimal("0.02"),
                        new BigDecimal("1"),
                        new BigDecimal("2")
                )
        ));

        mockMvc.perform(get("/api/v1/ai/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.providerId").value("deepseek"))
                .andExpect(jsonPath("$.data.apiKeySaved").value(true))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("sk-...abcd"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.pricing.currency").value("CNY"));
    }

    @Test
    void saveSettings_delegatesToServiceAndReturnsMaskedSettings() throws Exception {
        AiProviderSettingsSaveRequest request = new AiProviderSettingsSaveRequest(
                true,
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                "sk-raw-secret",
                true,
                0.4d,
                4096,
                new AiProviderPricing(
                        "CNY",
                        new BigDecimal("0.02"),
                        new BigDecimal("1"),
                        new BigDecimal("2")
                )
        );
        when(service.saveSettings(any(AiProviderSettingsSaveRequest.class))).thenReturn(new AiProviderSettings(
                true,
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-v4-flash",
                true,
                "sk-...cret",
                0.4d,
                4096,
                request.pricing()
        ));

        mockMvc.perform(put("/api/v1/ai/settings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("sk-...cret"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }
}
