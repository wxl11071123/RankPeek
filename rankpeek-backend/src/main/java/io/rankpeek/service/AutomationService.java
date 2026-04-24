package io.rankpeek.service;

import io.rankpeek.config.AppConfig;
import io.rankpeek.model.ChampionSelectSession;
import io.rankpeek.model.GamePhase;
import io.rankpeek.model.Lobby;
import io.rankpeek.model.Summoner;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 自动化服务
 * 提供自动匹配、自动接受、自动选人/禁人功能
 */
@Slf4j
@Service
public class AutomationService extends BaseAutomationTask {

    private final GameFlowService gameFlowService;
    private final ChampionSelectService championSelectService;
    private final SummonerService summonerService;
    private final AppConfig appConfig;

    public static final String TASK_AUTO_MATCH = "auto_match";
    public static final String TASK_AUTO_ACCEPT = "auto_accept";
    public static final String TASK_AUTO_PICK = "auto_pick";
    public static final String TASK_AUTO_BAN = "auto_ban";

    public AutomationService(GameFlowService gameFlowService,
                             ChampionSelectService championSelectService,
                             SummonerService summonerService,
                             AppConfig appConfig) {
        super(Executors.newScheduledThreadPool(4), new ConcurrentHashMap<>());
        this.gameFlowService = gameFlowService;
        this.championSelectService = championSelectService;
        this.summonerService = summonerService;
        this.appConfig = appConfig;
    }

    @PreDestroy
    public void destroy() {
        log.info("停止所有自动化任务...");
        stopAllTasks();
        scheduler.shutdown();
    }

    public void startAutoMatch() {
        startTask(TASK_AUTO_MATCH, this::autoMatchTask, 0, 1, TimeUnit.SECONDS);
    }

    public void startAutoAccept() {
        startTask(TASK_AUTO_ACCEPT, this::autoAcceptTask, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void startAutoPick() {
        startTask(TASK_AUTO_PICK, this::autoPickTask, 0, 2, TimeUnit.SECONDS);
    }

    public void startAutoBan() {
        startTask(TASK_AUTO_BAN, this::autoBanTask, 0, 2, TimeUnit.SECONDS);
    }

    public void stopAutoMatch() {
        stopTask(TASK_AUTO_MATCH);
    }

    public void stopAutoAccept() {
        stopTask(TASK_AUTO_ACCEPT);
    }

    public void stopAutoPick() {
        stopTask(TASK_AUTO_PICK);
    }

    public void stopAutoBan() {
        stopTask(TASK_AUTO_BAN);
    }

    public Map<String, Boolean> getTaskStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put(TASK_AUTO_MATCH, isTaskRunning(TASK_AUTO_MATCH));
        status.put(TASK_AUTO_ACCEPT, isTaskRunning(TASK_AUTO_ACCEPT));
        status.put(TASK_AUTO_PICK, isTaskRunning(TASK_AUTO_PICK));
        status.put(TASK_AUTO_BAN, isTaskRunning(TASK_AUTO_BAN));
        return status;
    }

    private void autoMatchTask() {
        try {
            String phase = gameFlowService.getGamePhase();

            if (!GamePhase.LOBBY.getCode().equalsIgnoreCase(phase)) {
                return;
            }

            Lobby lobby = gameFlowService.getLobby();
            if (lobby == null) {
                return;
            }

            if (lobby.getGameConfig() != null &&
                Boolean.TRUE.equals(lobby.getGameConfig().getIsCustom())) {
                log.debug("自定义游戏，跳过自动匹配");
                return;
            }

            Summoner me = summonerService.getMySummoner();
            if (me == null) {
                return;
            }

            if (!lobby.isLeader(me.getPuuid())) {
                log.debug("不是房主，跳过自动匹配");
                return;
            }

            log.info("自动开始匹配");
            gameFlowService.startMatchmaking();

            Thread.sleep(6000);

        } catch (Exception e) {
            log.error("自动匹配任务错误：{}", e.getMessage());
        }
    }

    private void autoAcceptTask() {
        try {
            String phase = gameFlowService.getGamePhase();

            if (GamePhase.READYCHECK.getCode().equalsIgnoreCase(phase)) {
                log.info("检测到确认对局阶段，自动接受");
                gameFlowService.acceptMatch();
            }
        } catch (Exception e) {
            log.error("自动接受任务错误：{}", e.getMessage());
        }
    }

    private void autoPickTask() {
        try {
            String phase = gameFlowService.getGamePhase();

            if (!GamePhase.CHAMPSELECT.getCode().equalsIgnoreCase(phase)) {
                return;
            }

            ChampionSelectSession session = championSelectService.getChampionSelectSession();
            if (session == null) {
                return;
            }

            int myCellId = session.getLocalPlayerCellId();
            log.debug("当前 Cell ID: {}", myCellId);

            List<Integer> pickChampions = appConfig.getPickChampions();
            if (pickChampions.isEmpty()) {
                log.warn("未配置选择英雄列表");
                return;
            }

            Set<Integer> unavailableChampions = new HashSet<>();

            for (List<ChampionSelectSession.Action> actionGroup : session.getActions()) {
                if (!actionGroup.isEmpty() && "ban".equals(actionGroup.getFirst().getActionType())) {
                    for (ChampionSelectSession.Action action : actionGroup) {
                        if (action.getActorCellId() != myCellId &&
                            Boolean.TRUE.equals(action.getCompleted())) {
                            unavailableChampions.add(action.getChampionId());
                        }
                    }
                }
            }

            for (List<ChampionSelectSession.Action> actionGroup : session.getActions()) {
                if (!actionGroup.isEmpty() && "pick".equals(actionGroup.getFirst().getActionType())) {
                    for (ChampionSelectSession.Action action : actionGroup) {
                        if (action.getActorCellId() != myCellId &&
                            action.getChampionId() != null &&
                            action.getChampionId() != 0) {
                            unavailableChampions.add(action.getChampionId());
                        }
                    }
                }
            }

            int championToPick = pickChampions.stream()
                    .filter(id -> !unavailableChampions.contains(id))
                    .findFirst()
                    .orElse(1);

            for (List<ChampionSelectSession.Action> actionGroup : session.getActions()) {
                if (!actionGroup.isEmpty() && "pick".equals(actionGroup.getFirst().getActionType())) {
                    for (ChampionSelectSession.Action action : actionGroup) {
                        if (action.getActorCellId() == myCellId) {
                            if (Boolean.TRUE.equals(action.getIsInProgress()) &&
                                !Boolean.TRUE.equals(action.getCompleted())) {
                                log.info("自动选择英雄：{}", championToPick);
                                championSelectService.pickChampion(action.getId(), championToPick, true);
                            } else if (action.getChampionId() == null || action.getChampionId() == 0) {
                                log.info("预选英雄：{}", championToPick);
                                championSelectService.pickChampion(action.getId(), championToPick, false);
                            }
                            return;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("自动选人任务错误：{}", e.getMessage());
        }
    }

    private void autoBanTask() {
        try {
            String phase = gameFlowService.getGamePhase();

            if (!GamePhase.CHAMPSELECT.getCode().equalsIgnoreCase(phase)) {
                return;
            }

            ChampionSelectSession session = championSelectService.getChampionSelectSession();
            if (session == null) {
                return;
            }

            int myCellId = session.getLocalPlayerCellId();

            List<Integer> banChampions = appConfig.getBanChampions();
            if (banChampions.isEmpty()) {
                log.warn("未配置禁用英雄列表");
                return;
            }

            Set<Integer> unavailableChampions = new HashSet<>();
            boolean alreadyBanned = false;

            for (List<ChampionSelectSession.Action> actionGroup : session.getActions()) {
                if (!actionGroup.isEmpty() && "ban".equals(actionGroup.get(0).getActionType())) {
                    for (ChampionSelectSession.Action action : actionGroup) {
                        if (action.getActorCellId() == myCellId) {
                            if (Boolean.TRUE.equals(action.getCompleted())) {
                                alreadyBanned = true;
                            }
                        } else if (Boolean.TRUE.equals(action.getCompleted())) {
                            unavailableChampions.add(action.getChampionId());
                        }
                    }
                }
            }

            if (alreadyBanned) {
                return;
            }

            for (List<ChampionSelectSession.Action> actionGroup : session.getActions()) {
                if (!actionGroup.isEmpty() && "pick".equals(actionGroup.get(0).getActionType())) {
                    for (ChampionSelectSession.Action action : actionGroup) {
                        if (action.getActorCellId() != myCellId &&
                            action.getChampionId() != null &&
                            action.getChampionId() != 0) {
                            unavailableChampions.add(action.getChampionId());
                        }
                    }
                }
            }

            int championToBan = banChampions.stream()
                    .filter(id -> !unavailableChampions.contains(id))
                    .findFirst()
                    .orElse(1);

            for (List<ChampionSelectSession.Action> actionGroup : session.getActions()) {
                if (!actionGroup.isEmpty() && "ban".equals(actionGroup.get(0).getActionType())) {
                    for (ChampionSelectSession.Action action : actionGroup) {
                        if (action.getActorCellId() == myCellId &&
                            Boolean.TRUE.equals(action.getIsInProgress())) {
                            log.info("自动禁用英雄：{}", championToBan);
                            championSelectService.banChampion(action.getId(), championToBan, true);
                            return;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("自动禁人任务错误：{}", e.getMessage());
        }
    }

    protected void stopTask(String taskName) {
        ScheduledFuture<?> task = tasks.remove(taskName);
        if (task != null) {
            task.cancel(false);
        }
    }


    public void setTaskEnabled(String taskName, boolean enabled) {
        switch (taskName) {
            case TASK_AUTO_MATCH -> {
                if (enabled) startAutoMatch();
                else stopAutoMatch();
            }
            case TASK_AUTO_ACCEPT -> {
                if (enabled) startAutoAccept();
                else stopAutoAccept();
            }
            case TASK_AUTO_PICK -> {
                if (enabled) startAutoPick();
                else stopAutoPick();
            }
            case TASK_AUTO_BAN -> {
                if (enabled) startAutoBan();
                else stopAutoBan();
            }
        }
    }
}
