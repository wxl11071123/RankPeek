package io.rankpeek.controller;

import io.rankpeek.model.ApiResponse;
import io.rankpeek.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 自动化控制器
 */
@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;

    /**
     * 获取所有任务状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> getTaskStatus() {
        return ApiResponse.success(automationService.getTaskStatus());
    }

    // ========== 自动匹配 ==========

    /**
     * 启动自动匹配
     */
    @PostMapping("/match/start")
    public ApiResponse<Void> startAutoMatch() {
        automationService.startAutoMatch();
        return ApiResponse.success();
    }

    /**
     * 停止自动匹配
     */
    @PostMapping("/match/stop")
    public ApiResponse<Void> stopAutoMatch() {
        automationService.stopAutoMatch();
        return ApiResponse.success();
    }

    // ========== 自动接受 ==========

    /**
     * 设置自动接受
     */
    @PostMapping("/accept/{enabled}")
    public ApiResponse<Void> setAutoAccept(@PathVariable boolean enabled) {
        automationService.setTaskEnabled(AutomationService.TASK_AUTO_ACCEPT, enabled);
        return ApiResponse.success();
    }

    // ========== 自动选人 ==========

    /**
     * 设置自动选人
     */
    @PostMapping("/pick/{enabled}")
    public ApiResponse<Void> setAutoPick(@PathVariable boolean enabled) {
        automationService.setTaskEnabled(AutomationService.TASK_AUTO_PICK, enabled);
        return ApiResponse.success();
    }

    // ========== 自动禁人 ==========

    /**
     * 设置自动禁人
     */
    @PostMapping("/ban/{enabled}")
    public ApiResponse<Void> setAutoBan(@PathVariable boolean enabled) {
        automationService.setTaskEnabled(AutomationService.TASK_AUTO_BAN, enabled);
        return ApiResponse.success();
    }

    /**
     * 批量设置自动化状态
     */
    @PostMapping("/batch")
    public ApiResponse<Void> setBatchAutomation(@RequestBody Map<String, Boolean> settings) {
        settings.forEach((key, enabled) -> {
            switch (key) {
                case "autoMatch" -> automationService.setTaskEnabled(AutomationService.TASK_AUTO_MATCH, enabled);
                case "autoAccept" -> automationService.setTaskEnabled(AutomationService.TASK_AUTO_ACCEPT, enabled);
                case "autoPick" -> automationService.setTaskEnabled(AutomationService.TASK_AUTO_PICK, enabled);
                case "autoBan" -> automationService.setTaskEnabled(AutomationService.TASK_AUTO_BAN, enabled);
            }
        });
        return ApiResponse.success();
    }
}
