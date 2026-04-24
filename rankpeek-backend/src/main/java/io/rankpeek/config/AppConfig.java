package io.rankpeek.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 应用配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    /**
     * 设置项
     */
    private Settings settings = new Settings();

    /**
     * 动态配置存储
     */
    private final Map<String, Object> dynamicConfig = new ConcurrentHashMap<>();

    @Data
    public static class Settings {
        private Auto auto = new Auto();
        private Match match = new Match();
    }

    @Data
    public static class Auto {
        /**
         * 自动开始匹配
         */
        private boolean startMatchSwitch = false;

        /**
         * 自动开始匹配延迟时间（秒）
         */
        private int startMatchDelay = 0;

        /**
         * 自动接受对局
         */
        private boolean acceptMatchSwitch = false;

        /**
         * 自动接受对局延迟时间（秒）
         */
        private int acceptMatchDelay = 0;

        /**
         * 自动选择英雄
         */
        private boolean pickChampionSwitch = false;

        /**
         * 自动禁用英雄
         */
        private boolean banChampionSwitch = false;

        /**
         * 选择英雄列表
         */
        private List<Integer> pickChampionSlice = new ArrayList<>();

        /**
         * 禁用英雄列表
         */
        private List<Integer> banChampionSlice = new ArrayList<>();
    }

    @Data
    public static class Match {
        /**
         * 战绩查询默认队列模式
         * 0=全部, 420=单双排, 440=灵活排位, 430=匹配, 450=大乱斗, 2400=海克斯大乱斗
         */
        private int defaultQueueMode = 0;
    }

    // ========== 便捷方法 ==========

    public boolean isAutoMatchEnabled() {
        return settings.getAuto().isStartMatchSwitch();
    }

    public int getAutoMatchDelay() {
        return settings.getAuto().getStartMatchDelay();
    }

    public boolean isAutoAcceptEnabled() {
        return settings.getAuto().isAcceptMatchSwitch();
    }

    public int getAutoAcceptDelay() {
        return settings.getAuto().getAcceptMatchDelay();
    }

    public boolean isAutoPickEnabled() {
        return settings.getAuto().isPickChampionSwitch();
    }

    public boolean isAutoBanEnabled() {
        return settings.getAuto().isBanChampionSwitch();
    }

    public List<Integer> getPickChampions() {
        return settings.getAuto().getPickChampionSlice();
    }

    public List<Integer> getBanChampions() {
        return settings.getAuto().getBanChampionSlice();
    }

    public int getDefaultMatchQueueMode() {
        return normalizeQueueMode(settings.getMatch().getDefaultQueueMode());
    }

    /**
     * 更新配置
     */
    public void updateConfig(String key, Object value) {
        dynamicConfig.put(key, value);

        // 同步更新内部设置
        updateInternalSettings(key, value);
    }

    private void updateInternalSettings(String key, Object value) {
        if ("settings".equals(key) && value instanceof Map<?, ?> settingsMap) {
            settingsMap.forEach((nestedKey, nestedValue) ->
                updateInternalSettings("settings." + nestedKey, nestedValue));
            return;
        }

        if ("settings.auto".equals(key) && value instanceof Map<?, ?> autoMap) {
            autoMap.forEach((nestedKey, nestedValue) ->
                updateInternalSettings("settings.auto." + nestedKey, nestedValue));
            return;
        }

        if ("settings.match".equals(key) && value instanceof Map<?, ?> matchMap) {
            matchMap.forEach((nestedKey, nestedValue) ->
                updateInternalSettings("settings.match." + nestedKey, nestedValue));
            return;
        }

        if (key.startsWith("settings.auto.")) {
            String autoKey = key.substring("settings.auto.".length());

            switch (autoKey) {
                case "startMatchSwitch" -> settings.getAuto().setStartMatchSwitch(toBoolean(value));
                case "startMatchDelay" -> settings.getAuto().setStartMatchDelay(clampDelay(toInt(value)));
                case "acceptMatchSwitch" -> settings.getAuto().setAcceptMatchSwitch(toBoolean(value));
                case "acceptMatchDelay" -> settings.getAuto().setAcceptMatchDelay(clampDelay(toInt(value)));
                case "pickChampionSwitch" -> settings.getAuto().setPickChampionSwitch(toBoolean(value));
                case "banChampionSwitch" -> settings.getAuto().setBanChampionSwitch(toBoolean(value));
                case "pickChampionSlice" -> {
                    if (value instanceof List<?> list) {
                        List<Integer> champions = list.stream()
                                .filter(item -> item instanceof Number)
                                .map(item -> ((Number) item).intValue())
                                .toList();
                        settings.getAuto().setPickChampionSlice(new ArrayList<>(champions));
                    }
                }
                case "banChampionSlice" -> {
                    if (value instanceof List<?> list) {
                        List<Integer> champions = list.stream()
                                .filter(item -> item instanceof Number)
                                .map(item -> ((Number) item).intValue())
                                .toList();
                        settings.getAuto().setBanChampionSlice(new ArrayList<>(champions));
                    }
                }
            }
        } else if (key.startsWith("settings.match.")) {
            String matchKey = key.substring("settings.match.".length());

            switch (matchKey) {
                case "defaultQueueMode" -> settings.getMatch().setDefaultQueueMode(normalizeQueueMode(toInt(value)));
            }
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b)
            return b;
        if (value instanceof String s)
            return Boolean.parseBoolean(s);
        if (value instanceof Map<?, ?> m && m.containsKey("value")) {
            return toBoolean(m.get("value"));
        }
        return false;
    }

    private int toInt(Object value) {
        if (value instanceof Number n)
            return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (value instanceof Map<?, ?> m && m.containsKey("value")) {
            return toInt(m.get("value"));
        }
        return 0;
    }

    private int clampDelay(int delay) {
        return Math.max(0, Math.min(10, delay));
    }

    private int normalizeQueueMode(int queueMode) {
        return switch (queueMode) {
            case 0, 420, 440, 430, 450, 2400 -> queueMode;
            default -> 0;
        };
    }

}
