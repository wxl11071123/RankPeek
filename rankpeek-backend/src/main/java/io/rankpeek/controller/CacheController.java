package io.rankpeek.controller;

import io.rankpeek.model.ApiResponse;
import io.rankpeek.model.CacheStatus;
import io.rankpeek.service.CacheStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheStatusService cacheStatusService;

    @GetMapping("/status")
    public ApiResponse<CacheStatus> getCacheStatus() {
        return ApiResponse.success(cacheStatusService.getStatus());
    }
}
