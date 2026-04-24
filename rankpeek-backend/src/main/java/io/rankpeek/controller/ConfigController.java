package io.rankpeek.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.rankpeek.config.AppConfig;
import io.rankpeek.model.ApiResponse;
import io.rankpeek.service.AssetService;
import io.rankpeek.service.AutomationService;

import lombok.RequiredArgsConstructor;

/**
 * 配置控制器
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppConfig appConfig;
    private final AutomationService automationService;
    private final AssetService assetService;

    /**
     * 获取所有配置
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getAllConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> settings = new HashMap<>();
        Map<String, Object> auto = new HashMap<>();
        Map<String, Object> match = new HashMap<>();

        auto.put("startMatchSwitch", appConfig.isAutoMatchEnabled());
        auto.put("acceptMatchSwitch", appConfig.isAutoAcceptEnabled());
        auto.put("pickChampionSwitch", appConfig.isAutoPickEnabled());
        auto.put("banChampionSwitch", appConfig.isAutoBanEnabled());
        auto.put("pickChampionSlice", appConfig.getPickChampions());
        auto.put("banChampionSlice", appConfig.getBanChampions());

        match.put("defaultQueueMode", appConfig.getDefaultMatchQueueMode());

        settings.put("auto", auto);
        settings.put("match", match);
        config.put("settings", settings);

        return ApiResponse.success(config);
    }

    /**
     * 获取指定配置项
     */
    @GetMapping("/{key}")
    public ApiResponse<Object> getConfig(@PathVariable String key) {
        return ApiResponse.success(getConfigValue(key));
    }

    /**
     * 更新配置
     */
    @PutMapping("/{key}")
    public ApiResponse<Void> setConfig(@PathVariable String key, @RequestBody Map<String, Object> body) {
        Object value = body.get("value");

        // 更新配置
        appConfig.updateConfig(key, value);

        // 触发自动化任务状态更新
        handleAutomationConfigChange(key, value);

        return ApiResponse.success();
    }

    /**
     * 获取英雄选项列表
     */
    @GetMapping("/champions")
    public ApiResponse<List<AssetService.ChampionOption>> getChampionOptions() {
        return ApiResponse.success(assetService.getChampionOptions());
    }

    /**
     * 获取游戏模式列表
     */
    @GetMapping("/game-modes")
    public ApiResponse<List<Map<String, Object>>> getGameModes() {
        return ApiResponse.success(List.of(
                Map.of("id", 0, "name", "全部"),
                Map.of("id", 420, "name", "单排/双排"),
                Map.of("id", 440, "name", "灵活排位"),
                Map.of("id", 430, "name", "匹配模式"),
                Map.of("id", 450, "name", "极地大乱斗"),
                Map.of("id", 2400, "name", "海克斯大乱斗")));
    }

    // ========== 内部方法 ==========

    private Object getConfigValue(String key) {
        if (key.startsWith("settings.auto.")) {
            String autoKey = key.substring("settings.auto.".length());
            return switch (autoKey) {
                case "startMatchSwitch" -> appConfig.isAutoMatchEnabled();
                case "acceptMatchSwitch" -> appConfig.isAutoAcceptEnabled();
                case "pickChampionSwitch" -> appConfig.isAutoPickEnabled();
                case "banChampionSwitch" -> appConfig.isAutoBanEnabled();
                case "pickChampionSlice" -> appConfig.getPickChampions();
                case "banChampionSlice" -> appConfig.getBanChampions();
                default -> null;
            };
        }
        if (key.startsWith("settings.match.")) {
            String matchKey = key.substring("settings.match.".length());
            return switch (matchKey) {
                case "defaultQueueMode" -> appConfig.getDefaultMatchQueueMode();
                default -> null;
            };
        }
        return appConfig.getDynamicConfig().get(key);
    }

    private void handleAutomationConfigChange(String key, Object value) {
        boolean enabled = toBoolean(value);

        switch (key) {
            case "settings.auto.startMatchSwitch" ->
                automationService.setTaskEnabled(AutomationService.TASK_AUTO_MATCH, enabled);
            case "settings.auto.acceptMatchSwitch" ->
                automationService.setTaskEnabled(AutomationService.TASK_AUTO_ACCEPT, enabled);
            case "settings.auto.pickChampionSwitch" ->
                automationService.setTaskEnabled(AutomationService.TASK_AUTO_PICK, enabled);
            case "settings.auto.banChampionSwitch" ->
                automationService.setTaskEnabled(AutomationService.TASK_AUTO_BAN, enabled);
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b)
            return b;
        if (value instanceof Map<?, ?> m && m.containsKey("value")) {
            return toBoolean(m.get("value"));
        }
        return false;
    }
}
