package io.rankpeek.controller;

import io.rankpeek.constant.QueueType;
import io.rankpeek.model.*;
import io.rankpeek.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 召唤师控制器
 */
@RestController
@RequestMapping("/api/v1/summoner")
@RequiredArgsConstructor
public class SummonerController {

    private final SummonerService summonerService;
    private final RankService rankService;
    private final MatchHistoryService matchHistoryService;
    private final LcuHttpClient lcuHttpClient;

    /**
     * 获取当前登录的召唤师信息
     */
    @GetMapping("/me")
    public ApiResponse<Summoner> getMySummoner() {
        return ApiResponse.success(summonerService.getMySummoner());
    }

    /**
     * 根据 PUUID 获取召唤师信息
     */
    @GetMapping("/puuid/{puuid}")
    public ApiResponse<Summoner> getSummonerByPuuid(@PathVariable String puuid) {
        return ApiResponse.success(summonerService.getSummonerByPuuid(puuid));
    }

    /**
     * 根据名称获取召唤师信息
     */
    @GetMapping("/name/{name}")
    public ApiResponse<Summoner> getSummonerByName(@PathVariable String name) {
        return ApiResponse.success(summonerService.getSummonerByName(name));
    }

    /**
     * 获取召唤师段位信息
     */
    @GetMapping("/rank/{puuid}")
    public ApiResponse<Rank> getRank(@PathVariable String puuid) {
        return ApiResponse.success(rankService.getRankByPuuid(puuid));
    }

    /**
     * 获取召唤师战绩
     * @param puuid 玩家 PUUID
     * @param begIndex 起始索引（默认 0，inclusive）
     * @param endIndex 结束索引（默认 9，inclusive）
     */
    @GetMapping("/matches/{puuid}")
    public ApiResponse<List<MatchHistory>> getMatchHistory(
            @PathVariable String puuid,
            @RequestParam(defaultValue = "0") int begIndex,
            @RequestParam(defaultValue = "9") int endIndex) {
        List<MatchHistory> matches = matchHistoryService.getMatchHistory(puuid, begIndex, endIndex);
        // 填充中文游戏模式名称
        for (MatchHistory match : matches) {
            if (match.getQueueId() != null) {
                match.setQueueName(QueueType.getQueueNameCn(match.getQueueId()));
            }
        }
        return ApiResponse.success(matches);
    }

    /**
     * 获取筛选后的对局记录
     * @param puuid 玩家 PUUID
     * @param begIndex 起始索引
     * @param endIndex 结束索引
     * @param queueId 队列 ID（可选，<=0 表示不过滤）
     * @param championId 英雄 ID（可选，<=0 表示不过滤）
     * @param maxResults 最大返回数量（默认 10）
     */
    @GetMapping("/matches-filtered/{puuid}")
    public ApiResponse<List<MatchHistory>> getFilteredMatchHistory(
            @PathVariable String puuid,
            @RequestParam(defaultValue = "0") int begIndex,
            @RequestParam(defaultValue = "49") int endIndex,
            @RequestParam(required = false) Integer queueId,
            @RequestParam(required = false) Integer championId,
            @RequestParam(defaultValue = "10") int maxResults) {
        List<MatchHistory> matches = matchHistoryService.getFilteredMatchHistory(puuid, begIndex, endIndex, queueId, championId, maxResults);
        // 填充中文游戏模式名称
        for (MatchHistory match : matches) {
            if (match.getQueueId() != null) {
                match.setQueueName(QueueType.getQueueNameCn(match.getQueueId()));
            }
        }
        return ApiResponse.success(matches);
    }

    /**
     * 获取服务器名称
     * @param name 召唤师名称
     * @return 服务器中文名
     */
    @GetMapping("/platform/{name}")
    public ApiResponse<String> getPlatformName(@PathVariable String name) {
        return ApiResponse.success(summonerService.getPlatformName(name));
    }

    /**
     * 获取胜率统计
     * @param puuid 玩家 PUUID
     * @param mode 队列模式（可选，<=0 表示全部）
     * @return 胜率统计
     */
    @GetMapping("/win-rate/{puuid}")
    public ApiResponse<WinRate> getWinRate(
            @PathVariable String puuid,
            @RequestParam(required = false) Integer mode) {
        return ApiResponse.success(matchHistoryService.getWinRate(puuid, mode));
    }

    /**
     * 获取排位胜率统计（从战绩计算真实胜率）
     * @param puuid 玩家 PUUID
     * @return 包含单排和灵活组排的胜率统计
     */
    @GetMapping("/ranked-win-rates/{puuid}")
    public ApiResponse<Map<String, WinRate>> getRankedWinRates(@PathVariable String puuid) {
        return ApiResponse.success(matchHistoryService.getRankedWinRates(puuid));
    }

    /**
     * 获取单局详情
     * @param gameId 对局 ID
     * @return 对局详情
     */
    @GetMapping("/game-detail/{gameId}")
    public ApiResponse<GameDetail> getGameDetail(@PathVariable Long gameId) {
        return ApiResponse.success(matchHistoryService.getGameDetailById(gameId));
    }

    /**
     * 调试接口：获取原始段位 JSON
     */
    @GetMapping("/rank-raw/{puuid}")
    public ApiResponse<JsonNode> getRankRaw(@PathVariable String puuid) {
        String uri = String.format("lol-ranked/v1/ranked-stats/%s", puuid);
        return ApiResponse.success(lcuHttpClient.get(uri, JsonNode.class));
    }
}
