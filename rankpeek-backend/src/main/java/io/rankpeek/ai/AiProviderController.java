package io.rankpeek.ai;

import io.rankpeek.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiProviderController {

    private final AiProviderSettingsService service;

    public AiProviderController(AiProviderSettingsService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public ApiResponse<Map<String, Object>> providers() {
        return ApiResponse.success(Map.of("providers", service.listProviderProfiles()));
    }

    @GetMapping("/settings")
    public ApiResponse<AiProviderSettings> settings() {
        return ApiResponse.success(service.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<AiProviderSettings> save(@RequestBody AiProviderSettingsSaveRequest request) {
        return ApiResponse.success(service.saveSettings(request));
    }
}
