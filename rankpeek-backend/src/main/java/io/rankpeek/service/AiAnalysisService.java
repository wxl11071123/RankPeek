package io.rankpeek.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.rankpeek.config.AppConfig;
import io.rankpeek.model.AIAnalysisResult;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.Rank;
import io.rankpeek.model.SessionData;
import io.rankpeek.model.SessionSummoner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI 分析服务
 * 提供对局 AI 复盘分析功能
 * 使用 DeepSeek API
 */
@Slf4j
@Service
public class AiAnalysisService {

    private final MatchHistoryService matchHistoryService;
    private final AppConfig appConfig;
    private final Executor aiExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    private static final String DEFAULT_ENDPOINT = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public AiAnalysisService(
            MatchHistoryService matchHistoryService,
            AppConfig appConfig,
            @Qualifier("aiExecutor") Executor aiExecutor) {
        this.matchHistoryService = matchHistoryService;
        this.appConfig = appConfig;
        this.aiExecutor = aiExecutor;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private final Cache<String, String> analysisCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private static final String SYSTEM_PROMPT = """
            你是 RankPeek 的 LOL 锐评分析师。
            你的风格要求如下：
            1. 语气要尖锐直接，可以嘲讽，但禁止脏话、人身攻击、低俗羞辱和人格侮辱。
            2. 所有判断都必须先下结论，再给数据证据。
            3. 只能基于提供的数据发言，不得编造对线细节、团战时间点、沟通内容或装备故事。
            4. 每条关键判断至少绑定 2 个具体指标。
            5. 收尾必须给一句短促的“锐评”。
            6. 全程使用简体中文，少废话，别端水。
            """;

    private static final String SAFE_SYSTEM_PROMPT = """
            You are RankPeek's sharp League of Legends analyst.
            Rules:
            1. Always reply in Simplified Chinese.
            2. Be direct, sharp, and slightly mocking, but never use profanity, slurs, or personal abuse.
            3. Give the conclusion first, then the evidence.
            4. Every key judgment must cite concrete metrics from the provided data.
            5. Never invent lane details, fight timelines, voice comms, or item stories that are not in the data.
            6. End with one short closing roast line.
            """;

    public boolean isEnabled() {
        return appConfig.getSettings().getAi().isEnabled();
    }

    public AIAnalysisResult analyzeGameDetail(Long gameId, String mode, Integer participantId) {
        if (!isEnabled()) {
            return AIAnalysisResult.error("AI 分析功能已禁用");
        }

        String cacheKey = String.format("analysis_%s_%d_%s", mode, gameId,
                participantId != null ? participantId : "all");

        String cached = analysisCache.getIfPresent(cacheKey);
        if (cached != null) {
            return AIAnalysisResult.success(cached);
        }

        try {
            GameDetail gameDetail = matchHistoryService.getGameDetailById(gameId);
            if (gameDetail == null) {
                return AIAnalysisResult.error("未找到对局详情");
            }

            String prompt;
            if ("player".equals(mode) && participantId != null) {
                prompt = buildAsciiSharpPlayerAnalysisPrompt(gameDetail, participantId);
            } else {
                prompt = buildAsciiSharpOverviewAnalysisPrompt(gameDetail);
            }

            String result = callDeepSeekApi(prompt);
            if (result != null) {
                analysisCache.put(cacheKey, result);
                return AIAnalysisResult.success(result);
            }

            return AIAnalysisResult.error("AI 分析失败");

        } catch (Exception e) {
            log.error("AI 分析失败: {}", e.getMessage(), e);
            return AIAnalysisResult.error("AI 分析失败: " + e.getMessage());
        }
    }

    public AIAnalysisResult analyzeSessionData(SessionData sessionData, String mode) {
        if (!isEnabled()) {
            return AIAnalysisResult.error("AI 分析功能已禁用");
        }

        String contentHash = generateSessionHash(sessionData);
        String cacheKey = String.format("session_%s_%s", mode, contentHash);

        String cached = analysisCache.getIfPresent(cacheKey);
        if (cached != null) {
            return AIAnalysisResult.success(cached);
        }

        try {
            String prompt;
            if ("player".equals(mode)) {
                prompt = buildAsciiSharpSessionPlayerAnalysisPrompt(sessionData);
            } else {
                prompt = buildAsciiSharpSessionTeamAnalysisPrompt(sessionData);
            }

            String result = callDeepSeekApi(prompt);
            if (result != null) {
                analysisCache.put(cacheKey, result);
                return AIAnalysisResult.success(result);
            }

            return AIAnalysisResult.error("AI 分析失败");

        } catch (Exception e) {
            log.error("AI 分析失败: {}", e.getMessage(), e);
            return AIAnalysisResult.error("AI 分析失败: " + e.getMessage());
        }
    }

    private String generateSessionHash(SessionData sessionData) {
        StringBuilder sb = new StringBuilder();
        sb.append(sessionData.getQueueId()).append("|");
        sb.append(sessionData.getPhase()).append("|");

        if (sessionData.getTeamOne() != null) {
            sb.append("team1:");
            sessionData.getTeamOne().stream()
                    .filter(s -> s.getSummoner() != null && s.getSummoner().getPuuid() != null)
                    .map(s -> s.getSummoner().getPuuid())
                    .forEach(sb::append);
        }
        if (sessionData.getTeamTwo() != null) {
            sb.append("|team2:");
            sessionData.getTeamTwo().stream()
                    .filter(s -> s.getSummoner() != null && s.getSummoner().getPuuid() != null)
                    .map(s -> s.getSummoner().getPuuid())
                    .forEach(sb::append);
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    private String callDeepSeekApi(String prompt) {
        String apiKey = appConfig.getSettings().getAi().getApiKey();
        if (apiKey == null || apiKey.isEmpty() || "your-api-key-here".equals(apiKey)) {
            log.error("AI 调用失败: 未配置 DeepSeek API Key");
            return null;
        }

        String endpoint = appConfig.getSettings().getAi().getEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = DEFAULT_ENDPOINT;
        }

        String model = appConfig.getSettings().getAi().getModel();
        if (model == null || model.isEmpty()) {
            model = DEFAULT_MODEL;
        }

        String url = endpoint.endsWith("/") ? endpoint + "v1/chat/completions" : endpoint + "/v1/chat/completions";

        log.info("调用 DeepSeek AI: model={}, endpoint={}", model, endpoint);

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);

            ArrayNode messages = requestBody.putArray("messages");
            
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SAFE_SYSTEM_PROMPT);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON))
                    .build();

            long startTime = System.currentTimeMillis();

            try (Response response = httpClient.newCall(request).execute()) {
                long elapsed = System.currentTimeMillis() - startTime;

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("DeepSeek API 调用失败: code={}, body={}", response.code(), errorBody);
                    return null;
                }

                String responseBody = response.body() != null ? response.body().string() : null;
                if (responseBody == null || responseBody.isEmpty()) {
                    log.warn("DeepSeek 返回结果为空");
                    return null;
                }

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode choices = root.path("choices");

                if (choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).path("message").path("content").asText();
                    log.info("DeepSeek 响应成功: elapsed={}ms, contentLength={}", elapsed, content.length());
                    return content;
                } else {
                    log.warn("DeepSeek 返回格式异常: {}", responseBody);
                    return null;
                }
            }

        } catch (Exception e) {
            log.error("DeepSeek API 调用异常: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildOverviewAnalysisPrompt(GameDetail gameDetail) {
        Map<String, Object> snapshot = buildMatchSnapshot(gameDetail);

        return """
                你是 LOL 单场复盘分析师。请只基于下面这场比赛的数据做结论，不要编造对线细节、团战时间点或装备效果。

                【任务目标】
                请你判断这场比赛里：
                1. 谁最尽力
                2. 谁最犯罪
                3. 谁是被对位或被局势打爆的
                4. 谁属于被队友连累
                5. 胜负的核心原因是什么

                【硬性要求】
                - 每个判断都必须引用至少 2 个具体数据证据，例如 KDA、伤害占比、承伤占比、经济、参团率、推塔、死亡数。
                - 不要因为输了就默认某个人犯罪，也不要因为赢了就默认某个人尽力。
                - "被连累"只给在败方里数据明显完成职责、但团队整体明显失衡的人。
                - "被爆"优先看高死亡、低经济占比、低输出占比、低参团，或者同队里明显拖后腿。
                - 允许结论为"无人明显犯罪"或"多人都尽力"。
                - 语气直接，不中立纯锐评。

                【对局信息】
                队列ID：%d
                游戏模式：%s
                时长：%d分%d秒
                构筑类型：%s

                【全场数据快照】
                %s

                【输出格式】
                请严格按这个结构输出：

                ## 总体结论
                - 先用 2-3 句话总结胜负原因。

                ## 尽力榜
                - 只列 1-2 人。
                - 每人一行：名字 + 判定 + 证据。

                ## 犯罪榜
                - 只列 1-2 人。
                - 如果没有明显犯罪，明确写"本局无人明显犯罪"。

                ## 被爆点评
                - 点出 1-2 个最明显的崩点。

                ## 被连累点评
                - 如果有人属于被连累，说明他做到了什么、却被哪些队友问题拖垮。

                ## 关键证据
                - 用 3-5 条 bullet 收尾，每条都带数字。
                """.formatted(
                gameDetail.getQueueId(),
                gameDetail.getGameMode(),
                gameDetail.getGameDuration() / 60,
                gameDetail.getGameDuration() % 60,
                isAugmentMode(gameDetail.getQueueId()) ? "海克斯/强化局，优先看强化搭配" : "常规局，优先看符文与基础数据",
                toJsonString(snapshot));
    }

    private String buildPlayerAnalysisPrompt(GameDetail gameDetail, Integer participantId) {
        Map<String, Object> snapshot = buildMatchSnapshot(gameDetail);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) snapshot.get("players");

        Map<String, Object> targetPlayer = players.stream()
                .filter(p -> participantId.equals(((Number) p.get("participantId")).intValue()))
                .findFirst()
                .orElse(players.getFirst());

        List<Map<String, Object>> sameTeamPlayers = players.stream()
                .filter(p -> p.get("teamId").equals(targetPlayer.get("teamId")))
                .collect(Collectors.toList());

        List<Map<String, Object>> enemyPlayers = players.stream()
                .filter(p -> !p.get("teamId").equals(targetPlayer.get("teamId")))
                .collect(Collectors.toList());

        return """
                你是 LOL 单人复盘分析师。请围绕指定玩家，判断他这局到底属于"尽力、犯罪、被爆、被连累、正常发挥、carry全场"中的哪一类。

                【硬性要求】
                - 必须先给出唯一主标签，只能从：尽力 / 犯罪 / 被爆 / 被连累 / 正常发挥 / carry全场 中选一个。
                - 所有结论必须基于数据，至少引用 3 个具体指标。
                - 要区分"自己打得差"和"队友整体拖垮"这两种情况。
                - 如果是海克斯/强化模式，请结合强化数量和构筑方向，判断是否成型。
                - 不要空泛鼓励，不要写成攻略。

                【对局信息】
                游戏模式：%s
                时长：%d分%d秒
                构筑类型：%s

                【目标玩家】
                %s

                【同队玩家】
                %s

                【敌方玩家】
                %s

                【输出格式】
                请严格按这个结构输出：

                ## 玩家判定
                - 先写：名字  主标签。

                ## 为什么这么判
                - 用 3-4 条 bullet 解释，必须带数字。

                ## 他是怎么输/赢的
                - 说明是自己打出来的、被针对的、还是被队友带飞/拖累。

                ## 一句话锐评
                - 用一句短评收尾，不中立纯锐评。
                """.formatted(
                gameDetail.getGameMode(),
                gameDetail.getGameDuration() / 60,
                gameDetail.getGameDuration() % 60,
                isAugmentMode(gameDetail.getQueueId()) ? "海克斯/强化局" : "常规局",
                toJsonString(targetPlayer),
                toJsonString(sameTeamPlayers),
                toJsonString(enemyPlayers));
    }

    private String buildSessionTeamAnalysisPrompt(SessionData sessionData) {
        Map<String, Object> teamOneData = buildSessionTeamData(sessionData.getTeamOne(), "我方");
        Map<String, Object> teamTwoData = buildSessionTeamData(sessionData.getTeamTwo(), "敌方");

        return """
                你是 LOL 资深分析师，请在比赛开始前分析双方队伍的实力对比。

                【对局信息】
                游戏模式：%s
                队列ID：%d

                【我方队伍】
                %s

                【敌方队伍】
                %s

                【分析要求】
                1. 分析双方段位分布，判断哪边整体段位更高
                2. 分析双方近期战绩，找出状态好/差的玩家
                3. 标注预组队情况（如果有）
                4. 找出我方和敌方的关键玩家（大腿/突破口）
                5. 给出对局预测和注意事项

                【输出格式】
                请严格按这个结构输出：

                ## 段位对比
                - 简要对比双方段位分布

                ## 近期状态
                - 列出状态好的玩家和状态差的玩家

                ## 预组队情况
                - 如有预组队标记，说明可能的配合

                ## 关键玩家
                - 我方大腿 / 我方突破口
                - 敌方大腿 / 敌方突破口

                ## 对局建议
                - 给我方玩家的 2-3 条具体建议
                """.formatted(
                sessionData.getTypeCn(),
                sessionData.getQueueId(),
                toJsonString(teamOneData),
                toJsonString(teamTwoData));
    }

    private String buildSessionPlayerAnalysisPrompt(SessionData sessionData) {
        List<Map<String, Object>> allPlayers = new ArrayList<>();

        if (sessionData.getTeamOne() != null) {
            for (SessionSummoner s : sessionData.getTeamOne()) {
                allPlayers.add(buildSessionPlayerData(s, "我方"));
            }
        }
        if (sessionData.getTeamTwo() != null) {
            for (SessionSummoner s : sessionData.getTeamTwo()) {
                allPlayers.add(buildSessionPlayerData(s, "敌方"));
            }
        }

        return """
                你是 LOL 资深分析师，请分析当前对局中所有玩家的实力分布。

                【对局信息】
                游戏模式：%s
                队列ID：%d

                【全部玩家数据】
                %s

                【分析要求】
                1. 按实力将玩家分层（大腿、正常、突破口）
                2. 分析每个玩家的段位和近期战绩
                3. 标注预组队情况
                4. 给出对局预测

                【输出格式】
                请严格按这个结构输出：

                ## 实力分层
                - 大腿：列出玩家名和理由
                - 正常：列出玩家名
                - 突破口：列出玩家名和理由

                ## 分路分析
                - 简要分析各位置对位优劣势

                ## 预组队情况
                - 如有预组队标记，说明可能的配合

                ## 对局预测
                - 预测对局走向和关键点
                """.formatted(
                sessionData.getTypeCn(),
                sessionData.getQueueId(),
                toJsonString(allPlayers));
    }

    private String buildSharpOverviewAnalysisPrompt(GameDetail gameDetail) {
        Map<String, Object> snapshot = buildMatchSnapshot(gameDetail);

        return """
                你现在要做单局总览锐评，只能根据给定数据下结论。
                任务：
                1. 先用一句话打总分，直接说清这局是谁把局面打歪了。
                2. 点出最尽力的 1-2 人、最犯罪的 1-2 人、最被打穿的 1 人。
                3. 如果有人属于“被队友拖累”，要明确写出是谁、为什么；没有就直说没有。
                4. 总结胜负最关键的两条原因。

                规则：
                - 先结论，后证据。
                - 每个核心判断至少绑定 2 个具体数字。
                - 不能编造对线细节、团战时间点、指挥沟通或装备故事。
                - 可以尖锐，可以阴阳怪气，但不要脏话、不要人身攻击。

                对局信息：
                队列ID：%d
                模式：%s
                时长：%d分%d秒
                局型：%s

                数据快照：
                %s

                输出格式：
                ## 总结论
                - 先写一句最狠但有依据的总评。
                ## 尽力榜
                - 名字：结论。证据：...
                ## 犯罪榜
                - 名字：结论。证据：...
                - 如果没人明显犯罪，就写“这局没有单人独吞锅，但有人明显拖后腿”。
                ## 被打穿点
                - 只点最明显的 1 人或 1 条线。
                ## 胜负手
                - 列 2 条关键原因，每条都带数字。
                ## 一句话锐评
                - 用一句短评收尾，越短越狠，但别脱离数据。
                """.formatted(
                gameDetail.getQueueId(),
                gameDetail.getGameMode(),
                gameDetail.getGameDuration() / 60,
                gameDetail.getGameDuration() % 60,
                isAugmentMode(gameDetail.getQueueId()) ? "强化模式，优先看成型和伤害分配" : "常规模式，优先看KDA、经济和参团",
                toJsonString(snapshot));
    }

    private String buildSharpPlayerAnalysisPrompt(GameDetail gameDetail, Integer participantId) {
        Map<String, Object> snapshot = buildMatchSnapshot(gameDetail);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) snapshot.get("players");

        Map<String, Object> targetPlayer = players.stream()
                .filter(p -> participantId.equals(((Number) p.get("participantId")).intValue()))
                .findFirst()
                .orElse(players.getFirst());

        List<Map<String, Object>> sameTeamPlayers = players.stream()
                .filter(p -> p.get("teamId").equals(targetPlayer.get("teamId")))
                .collect(Collectors.toList());

        List<Map<String, Object>> enemyPlayers = players.stream()
                .filter(p -> !p.get("teamId").equals(targetPlayer.get("teamId")))
                .collect(Collectors.toList());

        return """
                你现在要做单人复盘锐评，只能围绕指定玩家说人话。
                任务：
                1. 先给这个人贴唯一主标签，只能从“尽力 / 犯罪 / 被打穿 / 被拖累 / 正常发挥 / carry全场”里选一个。
                2. 说清他到底是自己没打好，还是队友把局面玩烂了。
                3. 如果是强化模式，要顺带看他有没有成型。

                规则：
                - 先结论，后证据。
                - 至少引用 3 个具体指标。
                - 不准写空话，不准编造对线过程。
                - 语气要犀利，但不能脏。

                对局信息：
                模式：%s
                时长：%d分%d秒
                局型：%s

                目标玩家：
                %s

                同队玩家：
                %s

                敌方玩家：
                %s

                输出格式：
                ## 玩家判定
                - 先写：名字 + 主标签 + 一句结论。
                ## 证据链
                - 写 3-4 条，每条都带数字。
                ## 输赢归因
                - 说明他是自己打出来的、被对面狠狠干碎的，还是被队友拖进坑里的。
                ## 一句话锐评
                - 用一句短评收尾，别温柔。
                """.formatted(
                gameDetail.getGameMode(),
                gameDetail.getGameDuration() / 60,
                gameDetail.getGameDuration() % 60,
                isAugmentMode(gameDetail.getQueueId()) ? "强化模式，优先看是否成型" : "常规模式",
                toJsonString(targetPlayer),
                toJsonString(sameTeamPlayers),
                toJsonString(enemyPlayers));
    }

    private String buildSharpSessionTeamAnalysisPrompt(SessionData sessionData) {
        Map<String, Object> teamOneData = buildSessionTeamData(sessionData.getTeamOne(), "我方");
        Map<String, Object> teamTwoData = buildSessionTeamData(sessionData.getTeamTwo(), "敌方");

        return """
                你现在做的是开局前的房间队伍锐评，只能根据房间里这批人的近期数据说话。
                任务：
                1. 判断哪边纸面更硬，哪边更像突破口。
                2. 标出状态最猛的人和最让人心里发毛的人。
                3. 如果有预组队，要说明可能的联动价值或隐患。
                4. 给我方两条最实际的注意事项。

                规则：
                - 先下结论，再给证据。
                - 每个关键判断至少引用 2 个数字或标签依据。
                - 不要编造分路对线和 BP 细节。
                - 允许毒舌，但别骂街。

                对局信息：
                模式：%s
                队列ID：%d

                我方队伍：
                %s

                敌方队伍：
                %s

                输出格式：
                ## 总判断
                - 先写一句谁更占便宜，别拐弯。
                ## 关键人物
                - 我方大腿：
                - 我方突破口：
                - 敌方大腿：
                - 敌方突破口：
                ## 预组队信号
                - 有就说组合价值，没有就直说没有。
                ## 对局建议
                - 给我方 2-3 条实操建议。
                ## 一句话锐评
                - 用一句短评收尾。
                """.formatted(
                sessionData.getTypeCn(),
                sessionData.getQueueId(),
                toJsonString(teamOneData),
                toJsonString(teamTwoData));
    }

    private String buildSharpSessionPlayerAnalysisPrompt(SessionData sessionData) {
        List<Map<String, Object>> allPlayers = new ArrayList<>();

        if (sessionData.getTeamOne() != null) {
            for (SessionSummoner s : sessionData.getTeamOne()) {
                allPlayers.add(buildSessionPlayerData(s, "我方"));
            }
        }
        if (sessionData.getTeamTwo() != null) {
            for (SessionSummoner s : sessionData.getTeamTwo()) {
                allPlayers.add(buildSessionPlayerData(s, "敌方"));
            }
        }

        return """
                你现在做的是房间全员锐评，要把这批玩家按危险程度拆开讲。
                任务：
                1. 按“大腿 / 正常 / 突破口”给所有玩家分层。
                2. 点出最值得警惕的 2 个人和最容易爆雷的 2 个人。
                3. 说明预组队是否会放大某一边的优势。
                4. 最后给出整体胜率倾向。

                规则：
                - 先结论，后证据。
                - 每个重点人物至少给 2 个数字或标签证据。
                - 不准编造英雄克制、对线细节和 BP 过程。
                - 语气可以尖，但不要变成人身攻击。

                对局信息：
                模式：%s
                队列ID：%d

                全部玩家数据：
                %s

                输出格式：
                ## 实力分层
                - 大腿：
                - 正常：
                - 突破口：
                ## 风险人物
                - 最危险的 2 人：
                - 最容易出事的 2 人：
                ## 预组队影响
                - 有就说组合强点，没有就直说没有。
                ## 对局倾向
                - 写清更看好哪边，以及为什么。
                ## 一句话锐评
                - 用一句短评收尾。
                """.formatted(
                sessionData.getTypeCn(),
                sessionData.getQueueId(),
                toJsonString(allPlayers));
    }

    private String buildAsciiSharpOverviewAnalysisPrompt(GameDetail gameDetail) {
        Map<String, Object> snapshot = buildMatchSnapshot(gameDetail);

        return """
                Reply in Simplified Chinese.
                Task: produce a sharp full-game review based only on the provided data.
                Goals:
                1. Open with the overall verdict first.
                2. Name the 1-2 best performers.
                3. Name the 1-2 biggest liabilities.
                4. Point out the clearest collapse point.
                5. Summarize the 2 key reasons for the result.
                Rules:
                - Every important claim must cite at least 2 concrete metrics.
                - Do not invent lane details, fight timings, or comms.
                - Tone should be sharp and mocking, but never abusive.
                Match info:
                queueId=%d
                mode=%s
                duration=%d:%02d
                archetype=%s
                Snapshot:
                %s
                Output format:
                ## 总结论
                - one sharp conclusion first
                ## 尽力榜
                - player: judgment + evidence
                ## 犯罪榜
                - player: judgment + evidence
                ## 被打穿点
                - one clearest collapse point
                ## 胜负手
                - two reasons with numbers
                ## 一句话锐评
                - one short roast line
                """.formatted(
                gameDetail.getQueueId(),
                gameDetail.getGameMode(),
                gameDetail.getGameDuration() / 60,
                gameDetail.getGameDuration() % 60,
                isAugmentMode(gameDetail.getQueueId()) ? "augment-mode" : "standard-mode",
                toJsonString(snapshot));
    }

    private String buildAsciiSharpPlayerAnalysisPrompt(GameDetail gameDetail, Integer participantId) {
        Map<String, Object> snapshot = buildMatchSnapshot(gameDetail);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) snapshot.get("players");

        Map<String, Object> targetPlayer = players.stream()
                .filter(p -> participantId.equals(((Number) p.get("participantId")).intValue()))
                .findFirst()
                .orElse(players.getFirst());

        List<Map<String, Object>> sameTeamPlayers = players.stream()
                .filter(p -> p.get("teamId").equals(targetPlayer.get("teamId")))
                .collect(Collectors.toList());

        List<Map<String, Object>> enemyPlayers = players.stream()
                .filter(p -> !p.get("teamId").equals(targetPlayer.get("teamId")))
                .collect(Collectors.toList());

        return """
                Reply in Simplified Chinese.
                Task: produce a sharp single-player review using only the provided data.
                Goals:
                1. Give exactly one main label from: 尽力 / 犯罪 / 被打穿 / 被拖累 / 正常发挥 / carry全场.
                2. Explain whether the player carried, fed, got targeted, or got dragged down.
                3. If this is an augment mode, mention whether the build looks completed.
                Rules:
                - Lead with the verdict.
                - Cite at least 3 concrete metrics.
                - Do not invent lane details or hidden events.
                - Tone should be sharp, not abusive.
                Match info:
                mode=%s
                duration=%d:%02d
                archetype=%s
                Target:
                %s
                Allies:
                %s
                Enemies:
                %s
                Output format:
                ## 玩家判定
                - name + main label + one-line verdict
                ## 证据链
                - 3-4 bullets with numbers
                ## 输赢归因
                - explain whether the player made the game, lost the game, or got dragged
                ## 一句话锐评
                - one short roast line
                """.formatted(
                gameDetail.getGameMode(),
                gameDetail.getGameDuration() / 60,
                gameDetail.getGameDuration() % 60,
                isAugmentMode(gameDetail.getQueueId()) ? "augment-mode" : "standard-mode",
                toJsonString(targetPlayer),
                toJsonString(sameTeamPlayers),
                toJsonString(enemyPlayers));
    }

    private String buildAsciiSharpSessionTeamAnalysisPrompt(SessionData sessionData) {
        Map<String, Object> teamOneData = buildSessionTeamData(sessionData.getTeamOne(), "blue-side");
        Map<String, Object> teamTwoData = buildSessionTeamData(sessionData.getTeamTwo(), "red-side");

        return """
                Reply in Simplified Chinese.
                Task: produce a sharp pre-game room analysis for both teams.
                Goals:
                1. Decide which side looks stronger on paper.
                2. Point out the hottest player and the weakest link on each side.
                3. Mention premade signals and whether they are an advantage or a risk.
                4. Give 2-3 practical suggestions for our side.
                Rules:
                - Lead with the verdict.
                - Every key claim must cite at least 2 numbers or tag signals.
                - Do not invent draft details or lane matchups.
                - Tone should be sharp, not abusive.
                Room info:
                mode=%s
                queueId=%d
                Team one:
                %s
                Team two:
                %s
                Output format:
                ## 总判断
                - who looks favored and why
                ## 关键人物
                - 我方大腿:
                - 我方突破口:
                - 敌方大腿:
                - 敌方突破口:
                ## 预组队信号
                - explain the value or say none
                ## 对局建议
                - 2-3 practical tips
                ## 一句话锐评
                - one short roast line
                """.formatted(
                sessionData.getTypeCn(),
                sessionData.getQueueId(),
                toJsonString(teamOneData),
                toJsonString(teamTwoData));
    }

    private String buildAsciiSharpSessionPlayerAnalysisPrompt(SessionData sessionData) {
        List<Map<String, Object>> allPlayers = new ArrayList<>();

        if (sessionData.getTeamOne() != null) {
            for (SessionSummoner s : sessionData.getTeamOne()) {
                allPlayers.add(buildSessionPlayerData(s, "blue-side"));
            }
        }
        if (sessionData.getTeamTwo() != null) {
            for (SessionSummoner s : sessionData.getTeamTwo()) {
                allPlayers.add(buildSessionPlayerData(s, "red-side"));
            }
        }

        return """
                Reply in Simplified Chinese.
                Task: produce a sharp room-wide player ranking before the game starts.
                Goals:
                1. Split all visible players into 大腿 / 正常 / 突破口.
                2. Name the two scariest players and the two most fragile players.
                3. Explain whether premade groups amplify one side.
                4. Finish with a room-wide win tendency.
                Rules:
                - Lead with the verdict.
                - Every highlighted player needs at least 2 metric or tag references.
                - Do not invent draft details or lane matchups.
                - Tone should be sharp, not abusive.
                Room info:
                mode=%s
                queueId=%d
                Players:
                %s
                Output format:
                ## 实力分层
                - 大腿:
                - 正常:
                - 突破口:
                ## 风险人物
                - 最危险的2人:
                - 最容易出事的2人:
                ## 预组队影响
                - explain the value or say none
                ## 对局倾向
                - which side looks favored and why
                ## 一句话锐评
                - one short roast line
                """.formatted(
                sessionData.getTypeCn(),
                sessionData.getQueueId(),
                toJsonString(allPlayers));
    }

    private Map<String, Object> buildSessionTeamData(List<SessionSummoner> team, String teamName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("teamName", teamName);

        if (team == null || team.isEmpty()) {
            data.put("players", List.of());
            return data;
        }

        List<Map<String, Object>> players = new ArrayList<>();
        for (SessionSummoner s : team) {
            players.add(buildSessionPlayerData(s, teamName));
        }
        data.put("players", players);

        return data;
    }

    private Map<String, Object> buildSessionPlayerData(SessionSummoner s, String teamName) {
        Map<String, Object> playerData = new LinkedHashMap<>();

        playerData.put("team", teamName);
        playerData.put("championId", s.getChampionId());

        if (s.getSummoner() != null) {
            playerData.put("name", s.getSummoner().getFullName());
            playerData.put("level", s.getSummoner().getSummonerLevel());
        } else {
            playerData.put("name", "未知");
            playerData.put("level", 0);
        }

        if (s.getRank() != null && s.getRank().getQueueMap() != null) {
            Rank.QueueInfo soloRank = s.getRank().getQueueMap().getRankedSolo5x5();
            Rank.QueueInfo flexRank = s.getRank().getQueueMap().getRankedFlexSr();

            playerData.put("soloRank", soloRank != null ? soloRank.getDisplayRank() : "未定级");
            playerData.put("flexRank", flexRank != null ? flexRank.getDisplayRank() : "未定级");

            if (soloRank != null && soloRank.getTotalGames() > 0) {
                playerData.put("soloWinRate", String.format("%.1f%%", soloRank.calculateWinRate()));
            }
        } else {
            playerData.put("soloRank", "未定级");
            playerData.put("flexRank", "未定级");
        }

        if (s.getMatchHistory() != null && !s.getMatchHistory().isEmpty()) {
            int wins = getWins(s);
            playerData.put("recentGames", s.getMatchHistory().size());
            playerData.put("recentWins", wins);
            playerData.put("recentWinRate", String.format("%.1f%%", wins * 100.0 / s.getMatchHistory().size()));
        } else {
            playerData.put("recentGames", 0);
            playerData.put("recentWins", 0);
        }

        if (s.getUserTag() != null) {
            playerData.put("recordStatus", s.getUserTag().getRecordStatus());
            playerData.put("tags", s.getUserTag().getTag() != null
                    ? s.getUserTag().getTag().stream().map(tag -> tag.getTagName()).limit(3).toList()
                    : List.of());

            if (s.getUserTag().getRecentData() != null) {
                playerData.put("recentKda", s.getUserTag().getRecentData().getKda());
                playerData.put("tagWins", s.getUserTag().getRecentData().getSelectWins());
                playerData.put("tagLosses", s.getUserTag().getRecentData().getSelectLosses());
            }
        }

        if (s.getPreGroupMarkers() != null && s.getPreGroupMarkers().getName() != null
                && !s.getPreGroupMarkers().getName().isEmpty()) {
            playerData.put("preGroup", s.getPreGroupMarkers().getName());
        }

        return playerData;
    }

    private static int getWins(SessionSummoner s) {
        int wins = 0;
        String playerPuuid = s.getSummoner() != null ? s.getSummoner().getPuuid() : null;

        for (MatchHistory match : s.getMatchHistory()) {
            if (match.getParticipantIdentities() != null && match.getParticipants() != null && playerPuuid != null) {
                for (int i = 0; i < match.getParticipantIdentities().size()
                        && i < match.getParticipants().size(); i++) {
                    MatchHistory.ParticipantIdentity identity = match.getParticipantIdentities().get(i);
                    if (identity.getPlayer() != null && playerPuuid.equals(identity.getPlayer().getPuuid())) {
                        MatchHistory.Participant participant = match.getParticipants().get(i);
                        if (participant.getStats() != null && Boolean.TRUE.equals(participant.getStats().getWin())) {
                            wins++;
                        }
                        break;
                    }
                }
            }
        }
        return wins;
    }

    private Map<String, Object> buildMatchSnapshot(GameDetail gameDetail) {
        Map<Integer, Map<String, Long>> teamTotals = calculateTeamTotals(gameDetail.getParticipants());
        List<Map<String, Object>> players = buildPlayersData(gameDetail, teamTotals);
        List<Map<String, Object>> teams = buildTeamsData(players);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("gameId", gameDetail.getGameId());
        snapshot.put("queueId", gameDetail.getQueueId());
        snapshot.put("gameMode", gameDetail.getGameMode());
        snapshot.put("durationSeconds", gameDetail.getGameDuration());
        snapshot.put("augmentMode", isAugmentMode(gameDetail.getQueueId()));
        snapshot.put("teams", teams);
        snapshot.put("players", players);

        return snapshot;
    }

    private Map<Integer, Map<String, Long>> calculateTeamTotals(List<GameDetail.GameParticipant> participants) {
        Map<Integer, Map<String, Long>> teamTotals = new HashMap<>();

        for (GameDetail.GameParticipant participant : participants) {
            int teamId = participant.getTeamId();
            Map<String, Long> totals = teamTotals.computeIfAbsent(teamId, k -> new HashMap<>());
            GameDetail.Stats stats = participant.getStats();

            long damage = stats.getTotalDamageDealtToChampions() != null ? stats.getTotalDamageDealtToChampions() : 0L;
            long taken = stats.getTotalDamageTaken() != null ? stats.getTotalDamageTaken() : 0L;
            long gold = stats.getGoldEarned() != null ? stats.getGoldEarned() : 0L;
            long kills = stats.getKills() != null ? stats.getKills() : 0L;

            totals.merge("damage", damage, (a, b) -> a + b);
            totals.merge("taken", taken, (a, b) -> a + b);
            totals.merge("gold", gold, (a, b) -> a + b);
            totals.merge("kills", kills, (a, b) -> a + b);
        }

        return teamTotals;
    }

    private List<Map<String, Object>> buildPlayersData(
            GameDetail gameDetail,
            Map<Integer, Map<String, Long>> teamTotals) {

        List<Map<String, Object>> players = new ArrayList<>();

        for (GameDetail.GameParticipant participant : gameDetail.getParticipants()) {
            Map<String, Object> playerData = buildPlayerData(gameDetail, participant, teamTotals);
            players.add(playerData);
        }

        return players;
    }

    private Map<String, Object> buildPlayerData(
            GameDetail gameDetail,
            GameDetail.GameParticipant participant,
            Map<Integer, Map<String, Long>> teamTotals) {

        Map<String, Object> playerData = new LinkedHashMap<>();
        GameDetail.Stats stats = participant.getStats();
        Map<String, Long> totals = teamTotals.getOrDefault(participant.getTeamId(), new HashMap<>());

        playerData.put("participantId", participant.getParticipantId());
        playerData.put("teamId", participant.getTeamId());
        playerData.put("championId", participant.getChampionId());
        playerData.put("win", stats.getWin());

        playerData.put("name", getPlayerName(gameDetail, participant.getParticipantId()));

        int kills = stats.getKills() != null ? stats.getKills() : 0;
        int deaths = stats.getDeaths() != null ? stats.getDeaths() : 0;
        int assists = stats.getAssists() != null ? stats.getAssists() : 0;
        double kda = deaths > 0 ? (kills + assists) * 1.0 / deaths : kills + assists;

        playerData.put("kda", Math.round(kda * 100) / 100.0);
        playerData.put("kills", kills);
        playerData.put("deaths", deaths);
        playerData.put("assists", assists);

        long damage = stats.getTotalDamageDealtToChampions() != null ? stats.getTotalDamageDealtToChampions() : 0;
        long taken = stats.getTotalDamageTaken() != null ? stats.getTotalDamageTaken() : 0;
        long gold = stats.getGoldEarned() != null ? stats.getGoldEarned() : 0;

        playerData.put("gold", gold);
        playerData.put("damage", damage);
        playerData.put("taken", taken);

        playerData.put("damageShare", calculateShare(damage, totals.getOrDefault("damage", 1L)));
        playerData.put("takenShare", calculateShare(taken, totals.getOrDefault("taken", 1L)));
        playerData.put("goldShare", calculateShare(gold, totals.getOrDefault("gold", 1L)));
        playerData.put("killParticipation", calculateShare(kills + assists, totals.getOrDefault("kills", 1L)));

        playerData.put("perks", buildPerksData(stats));
        playerData.put("augments", buildAugmentsData(stats));

        return playerData;
    }

    private double calculateShare(long value, long total) {
        if (total <= 0)
            return 0;
        return Math.round(value * 100.0 / total * 10) / 10.0;
    }

    private Map<String, Integer> buildPerksData(GameDetail.Stats stats) {
        return Map.of(
                "primary", stats.getPerk0() != null ? stats.getPerk0() : 0,
                "subStyle", stats.getPerkSubStyle() != null ? stats.getPerkSubStyle() : 0);
    }

    private List<Integer> buildAugmentsData(GameDetail.Stats stats) {
        List<Integer> augments = new ArrayList<>();
        if (stats.getPlayerAugment1() != null && stats.getPlayerAugment1() > 0)
            augments.add(stats.getPlayerAugment1());
        if (stats.getPlayerAugment2() != null && stats.getPlayerAugment2() > 0)
            augments.add(stats.getPlayerAugment2());
        if (stats.getPlayerAugment3() != null && stats.getPlayerAugment3() > 0)
            augments.add(stats.getPlayerAugment3());
        if (stats.getPlayerAugment4() != null && stats.getPlayerAugment4() > 0)
            augments.add(stats.getPlayerAugment4());
        return augments;
    }

    private List<Map<String, Object>> buildTeamsData(List<Map<String, Object>> players) {
        List<Map<String, Object>> teams = new ArrayList<>();

        Map<Integer, List<Map<String, Object>>> teamPlayers = players.stream()
                .collect(Collectors.groupingBy(p -> (Integer) p.get("teamId")));

        for (Map.Entry<Integer, List<Map<String, Object>>> entry : teamPlayers.entrySet()) {
            Map<String, Object> teamData = new LinkedHashMap<>();
            teamData.put("teamId", entry.getKey());
            teamData.put("result", determineTeamResult(entry.getValue()));
            teamData.put("players", entry.getValue());
            teams.add(teamData);
        }

        teams.sort(Comparator.comparing(t -> !"胜方".equals(t.get("result"))));

        return teams;
    }

    private String determineTeamResult(List<Map<String, Object>> teamPlayers) {
        if (teamPlayers.isEmpty())
            return "未知";

        Boolean win = (Boolean) teamPlayers.get(0).get("win");
        return win != null && win ? "胜方" : "败方";
    }

    private String getPlayerName(GameDetail gameDetail, Integer participantId) {
        if (gameDetail.getParticipantIdentities() != null) {
            for (GameDetail.ParticipantIdentity identity : gameDetail.getParticipantIdentities()) {
                if (participantId.equals(identity.getParticipantId()) && identity.getPlayer() != null) {
                    GameDetail.Player player = identity.getPlayer();
                    if (player.getGameName() != null && !player.getGameName().isEmpty()) {
                        return player.getTagLine() != null ? player.getGameName() + "#" + player.getTagLine()
                                : player.getGameName();
                    }
                    return player.getSummonerName() != null ? player.getSummonerName() : "未知";
                }
            }
        }
        return "玩家" + participantId;
    }

    private boolean isAugmentMode(Integer queueId) {
        if (queueId == null)
            return false;
        return queueId == 1700 || queueId == 2400;
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    public CompletableFuture<AIAnalysisResult> analyzeGameDetailAsync(Long gameId, String mode, Integer participantId) {
        return CompletableFuture.supplyAsync(
                () -> analyzeGameDetail(gameId, mode, participantId),
                aiExecutor);
    }
}
