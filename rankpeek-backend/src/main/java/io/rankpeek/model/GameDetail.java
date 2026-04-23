package io.rankpeek.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 对局详情模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameDetail {

    @JsonProperty("gameId")
    private Long gameId;

    @JsonProperty("gameMode")
    private String gameMode;

    @JsonProperty("gameType")
    private String gameType;

    @JsonProperty("mapId")
    private Integer mapId;

    @JsonProperty("queueId")
    private Integer queueId;

    @JsonProperty("gameDuration")
    private Long gameDuration;

    @JsonProperty("gameCreation")
    private Long gameCreation;

    @JsonProperty("participantIdentities")
    private List<ParticipantIdentity> participantIdentities;

    @JsonProperty("participants")
    private List<GameParticipant> participants;

    /**
     * 参与者身份信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantIdentity {
        @JsonProperty("participantId")
        private Integer participantId;

        @JsonProperty("player")
        private Player player;
    }

    /**
     * 玩家信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        @JsonProperty("accountId")
        private Long accountId;

        @JsonProperty("puuid")
        private String puuid;

        @JsonProperty("platformId")
        private String platformId;

        @JsonProperty("summonerName")
        private String summonerName;

        @JsonProperty("gameName")
        private String gameName;

        @JsonProperty("tagLine")
        private String tagLine;

        @JsonProperty("summonerId")
        private Long summonerId;

        public String getFullName() {
            if (tagLine != null && !tagLine.isEmpty()) {
                return gameName + "#" + tagLine;
            }
            return gameName != null ? gameName : summonerName;
        }
    }

    /**
     * 对局参与者详细数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GameParticipant {
        @JsonProperty("participantId")
        private Integer participantId;

        @JsonProperty("teamId")
        private Integer teamId;

        @JsonProperty("championId")
        private Integer championId;

        @JsonProperty("spell1Id")
        private Integer spell1Id;

        @JsonProperty("spell2Id")
        private Integer spell2Id;

        @JsonProperty("stats")
        private Stats stats;

        @JsonProperty("timeline")
        private Timeline timeline;
    }

    /**
     * 统计数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stats {
        @JsonProperty("win")
        private Boolean win;

        @JsonProperty("kills")
        private Integer kills;

        @JsonProperty("deaths")
        private Integer deaths;

        @JsonProperty("assists")
        private Integer assists;

        @JsonProperty("totalMinionsKilled")
        private Integer totalMinionsKilled;

        @JsonProperty("neutralMinionsKilled")
        private Integer neutralMinionsKilled;

        @JsonProperty("goldEarned")
        private Long goldEarned;

        @JsonProperty("totalDamageDealtToChampions")
        private Long totalDamageDealtToChampions;

        @JsonProperty("visionWardsBoughtInGame")
        private Integer visionWardsBoughtInGame;

        @JsonProperty("wardsPlaced")
        private Integer wardsPlaced;

        @JsonProperty("wardsKilled")
        private Integer wardsKilled;

        @JsonProperty("largestMultiKill")
        private Integer largestMultiKill;

        @JsonProperty("totalHeal")
        private Long totalHeal;

        @JsonProperty("totalDamageTaken")
        private Long totalDamageTaken;

        @JsonProperty("doubleKills")
        private Integer doubleKills;

        @JsonProperty("tripleKills")
        private Integer tripleKills;

        @JsonProperty("quadraKills")
        private Integer quadraKills;

        @JsonProperty("pentaKills")
        private Integer pentaKills;

        // 装备
        @JsonProperty("item0")
        private Integer item0;

        @JsonProperty("item1")
        private Integer item1;

        @JsonProperty("item2")
        private Integer item2;

        @JsonProperty("item3")
        private Integer item3;

        @JsonProperty("item4")
        private Integer item4;

        @JsonProperty("item5")
        private Integer item5;

        @JsonProperty("item6")
        private Integer item6;

        // MVP/SVP
        @JsonProperty("mvp")
        private String mvp;

        // 伤害占比
        @JsonProperty("damageDealtToChampionsRate")
        private Double damageDealtToChampionsRate;

        @JsonProperty("damageTakenRate")
        private Double damageTakenRate;

        @JsonProperty("healRate")
        private Double healRate;

        @JsonProperty("damageDealtToTurrets")
        private Long damageDealtToTurrets;

        // 符文
        @JsonProperty("perk0")
        private Integer perk0;

        @JsonProperty("perk1")
        private Integer perk1;

        @JsonProperty("perk2")
        private Integer perk2;

        @JsonProperty("perk3")
        private Integer perk3;

        @JsonProperty("perk4")
        private Integer perk4;

        @JsonProperty("perk5")
        private Integer perk5;

        @JsonProperty("perkPrimaryStyle")
        private Integer perkPrimaryStyle;

        @JsonProperty("perkSubStyle")
        private Integer perkSubStyle;

        // 海克斯强化 (竞技场模式)
        @JsonProperty("playerAugment1")
        private Integer playerAugment1;

        @JsonProperty("playerAugment2")
        private Integer playerAugment2;

        @JsonProperty("playerAugment3")
        private Integer playerAugment3;

        @JsonProperty("playerAugment4")
        private Integer playerAugment4;
    }

    /**
     * 时间线数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Timeline {
        @JsonProperty("lane")
        private String lane;

        @JsonProperty("role")
        private String role;
    }
}
