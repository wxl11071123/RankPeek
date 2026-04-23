package io.rankpeek.service;

import io.rankpeek.model.Rank;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 段位数据服务
 * 提供召唤师段位信息查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankService {

    private final LcuHttpClient lcuHttpClient;

    private Cache<String, Rank> rankCache;

    @PostConstruct
    public void init() {
        this.rankCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        log.info("段位服务初始化完成");
    }

    /**
     * 获取召唤师段位信息
     */
    public Rank getRankByPuuid(String puuid) {
        return rankCache.get(puuid, key -> {
            String uri = String.format("lol-ranked/v1/ranked-stats/%s", key);
            Rank rank = lcuHttpClient.get(uri, Rank.class);
            
            if (rank != null && rank.getQueueMap() != null) {
                if (rank.getQueueMap().getRankedSolo5x5() != null) {
                    var solo = rank.getQueueMap().getRankedSolo5x5();
                    log.info("单双排段位原始数据 - tier: {}, wins: {}, losses: {}, games: {}",
                            solo.getTier(), solo.getWins(), solo.getLosses(), solo.getGames());
                }
                if (rank.getQueueMap().getRankedFlexSr() != null) {
                    var flex = rank.getQueueMap().getRankedFlexSr();
                    log.info("灵活组排段位原始数据 - tier: {}, wins: {}, losses: {}, games: {}",
                            flex.getTier(), flex.getWins(), flex.getLosses(), flex.getGames());
                }
            }
            return rank;
        });
    }

    /**
     * 刷新指定召唤师段位缓存
     */
    public void refreshCache(String puuid) {
        rankCache.invalidate(puuid);
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAllCache() {
        rankCache.invalidateAll();
    }
}
