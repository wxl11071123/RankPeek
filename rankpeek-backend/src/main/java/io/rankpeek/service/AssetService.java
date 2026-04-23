package io.rankpeek.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏资源服务
 * 管理英雄、装备、符文等游戏资源数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final LcuHttpClient lcuHttpClient;

    // 英雄缓存
    private final Map<Long, Champion> championCache = new ConcurrentHashMap<>();
    // 装备缓存 (id -> iconPath)
    private final Map<Long, String> itemIconPathCache = new ConcurrentHashMap<>();
    // 召唤师技能缓存 (id -> iconPath)
    private final Map<Long, String> spellIconPathCache = new ConcurrentHashMap<>();
    // 海克斯强化缓存 (id -> iconPath)
    private final Map<Long, String> augmentIconPathCache = new ConcurrentHashMap<>();
    // 海克斯强化稀有度缓存 (id -> rarity)
    private final Map<Long, String> augmentRarityCache = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("初始化资源服务...");
        // 异步加载资源
        new Thread(this::loadAssets, "asset-loader").start();
    }

    /**
     * 加载游戏资源
     */
    private void loadAssets() {
        try {
            loadChampions();
            loadItems();
            loadSpells();
            loadAugments();
            log.info("资源加载完成，英雄: {}, 装备: {}, 技能: {}, 海克斯: {}",
                    championCache.size(), itemIconPathCache.size(), spellIconPathCache.size(), augmentIconPathCache.size());
        } catch (Exception e) {
            log.error("加载资源失败: {}", e.getMessage());
        }
    }

    /**
     * 加载英雄列表
     */
    private void loadChampions() {
        try {
            Champion[] champions = lcuHttpClient.get("lol-game-data/assets/v1/champion-summary", Champion[].class);
            if (champions != null) {
                for (Champion champion : champions) {
                    if (champion.id > 0) { // 排除无效 ID
                        championCache.put(champion.id, champion);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 LCU 加载英雄失败，使用内置数据: {}", e.getMessage());
            loadBuiltInChampions();
        }
    }

    /**
     * 加载装备列表
     */
    private void loadItems() {
        try {
            Item[] items = lcuHttpClient.get("lol-game-data/assets/v1/items.json", Item[].class);
            if (items != null) {
                for (Item item : items) {
                    if (item.id > 0 && item.iconPath != null && !item.iconPath.isEmpty()) {
                        itemIconPathCache.put(item.id, item.iconPath);
                    }
                }
            }
            log.info("装备加载完成: {}", itemIconPathCache.size());
        } catch (Exception e) {
            log.warn("加载装备失败: {}", e.getMessage());
        }
    }

    /**
     * 加载召唤师技能列表
     */
    private void loadSpells() {
        try {
            Spell[] spells = lcuHttpClient.get("lol-game-data/assets/v1/summoner-spells.json", Spell[].class);
            if (spells != null) {
                for (Spell spell : spells) {
                    if (spell.id > 0 && spell.iconPath != null && !spell.iconPath.isEmpty()) {
                        spellIconPathCache.put(spell.id, spell.iconPath);
                    }
                }
            }
            log.info("召唤师技能加载完成: {}", spellIconPathCache.size());
        } catch (Exception e) {
            log.warn("加载召唤师技能失败: {}", e.getMessage());
        }
    }

    /**
     * 加载内置英雄数据（备用）
     */
    private void loadBuiltInChampions() {
        // 常用英雄数据
        Map<Long, Champion> builtIn = Map.ofEntries(
                createChampion(1, "安妮", "Annie"),
                createChampion(2, "奥拉夫", "Olaf"),
                createChampion(3, "加里奥", "Galio"),
                createChampion(4, "卡牌大师", "TwistedFate"),
                createChampion(5, "希瓦娜", "Shyvana"),
                createChampion(6, "厄加特", "Urgot"),
                createChampion(7, "乐芙兰", "Leblanc"),
                createChampion(8, "弗拉基米尔", "Vladimir"),
                createChampion(9, "费德提克", "FiddleSticks"),
                createChampion(10, "凯尔", "Kayle"),
                createChampion(11, "易", "MasterYi"),
                createChampion(12, "阿利斯塔", "Alistar"),
                createChampion(13, "瑞兹", "Ryze"),
                createChampion(14, "塞恩", "Sion"),
                createChampion(15, "希维尔", "Sivir"),
                createChampion(16, "索拉卡", "Soraka"),
                createChampion(17, "提莫", "Teemo"),
                createChampion(18, "崔丝塔娜", "Tristana"),
                createChampion(19, "沃里克", "Warwick"),
                createChampion(20, "努努", "Nunu"),
                createChampion(21, "厄运小姐", "MissFortune"),
                createChampion(22, "艾希", "Ashe"),
                createChampion(23, "泰达米尔", "Tryndamere"),
                createChampion(24, "贾克斯", "Jax"),
                createChampion(25, "莫甘娜", "Morgana"),
                createChampion(26, "基兰", "Zilean"),
                createChampion(27, "辛吉德", "Singed"),
                createChampion(28, "伊芙琳", "Evelynn"),
                createChampion(29, "塔莉垭", "Taliyah"),
                createChampion(30, "卡莎", "Kaisa"),
                createChampion(31, "科加斯", "Chogath"),
                createChampion(32, "阿木木", "Amumu"),
                createChampion(33, "拉莫斯", "Rammus"),
                createChampion(34, "艾尼维亚", "Anivia"),
                createChampion(35, "萨科", "Shaco"),
                createChampion(36, "墨菲特", "Malphite"),
                createChampion(37, "娑娜", "Sona"),
                createChampion(38, "卡萨丁", "Kassadin"),
                createChampion(39, "艾瑞莉娅", "Irelia"),
                createChampion(40, "迦娜", "Janna"),
                createChampion(41, "普朗克", "Gangplank"),
                createChampion(42, "库奇", "Corki"),
                createChampion(43, "卡尔玛", "Karma"),
                createChampion(44, "塔里克", "Taric"),
                createChampion(45, "维迦", "Veigar"),
                createChampion(48, "特朗德尔", "Trundle"),
                createChampion(50, "斯维因", "Swain"),
                createChampion(51, "凯特琳", "Caitlyn"),
                createChampion(52, "潘森", "Pantheon"),
                createChampion(53, "布里茨", "Blitzcrank"),
                createChampion(54, "墨菲特", "Malphite"),
                createChampion(55, "卡特琳娜", "Katarina"),
                createChampion(56, "梦魇", "Nocturne"),
                createChampion(57, "茂凯", "Maokai"),
                createChampion(58, "雷克顿", "Renekton"),
                createChampion(59, "嘉文四世", "JarvanIV"),
                createChampion(60, "伊莉丝", "Elise"),
                createChampion(61, "奥莉安娜", "Orianna"),
                createChampion(62, "孙悟空", "MonkeyKing"),
                createChampion(63, "布兰德", "Brand"),
                createChampion(64, "李青", "LeeSin"),
                createChampion(67, "薇恩", "Vayne"),
                createChampion(68, "兰博", "Rumble"),
                createChampion(69, "卡西奥佩娅", "Cassiopeia"),
                createChampion(72, "斯卡纳", "Skarner"),
                createChampion(74, "黑默丁格", "Heimerdinger"),
                createChampion(75, "内瑟斯", "Nasus"),
                createChampion(76, "奈德丽", "Nidalee"),
                createChampion(77, "乌迪尔", "Udyr"),
                createChampion(78, "波比", "Poppy"),
                createChampion(79, "古拉加斯", "Gragas"),
                createChampion(80, "潘森", "Pantheon"),
                createChampion(81, "伊泽瑞尔", "Ezreal"),
                createChampion(82, "莫德凯撒", "Mordekaiser"),
                createChampion(83, "约里克", "Yorick"),
                createChampion(84, "阿卡丽", "Akali"),
                createChampion(85, "凯南", "Kennen"),
                createChampion(86, "盖伦", "Garen"),
                createChampion(89, "蕾欧娜", "Leona"),
                createChampion(90, "玛尔扎哈", "Malzahar"),
                createChampion(91, "泰隆", "Talon"),
                createChampion(92, "锐雯", "Riven"),
                createChampion(96, "克格莫", "KogMaw"),
                createChampion(98, "慎", "Shen"),
                createChampion(99, "拉克丝", "Lux"),
                createChampion(101, "泽拉斯", "Xerath"),
                createChampion(102, "希瓦娜", "Shyvana"),
                createChampion(103, "阿狸", "Ahri"),
                createChampion(104, "格雷福斯", "Graves"),
                createChampion(105, "菲兹", "Fizz"),
                createChampion(106, "沃利贝尔", "Volibear"),
                createChampion(107, "雷恩加尔", "Rengar"),
                createChampion(110, "维鲁斯", "Varus"),
                createChampion(111, "诺提勒斯", "Nautilus"),
                createChampion(112, "维克托", "Viktor"),
                createChampion(113, "瑟庄妮", "Sejuani"),
                createChampion(114, "菲奥娜", "Fiora"),
                createChampion(115, "吉格斯", "Ziggs"),
                createChampion(117, "璐璐", "Lulu"),
                createChampion(119, "德莱文", "Draven"),
                createChampion(120, "赫卡里姆", "Hecarim"),
                createChampion(121, "卡兹克", "Khazix"),
                createChampion(122, "德莱厄斯", "Darius"),
                createChampion(126, "杰斯", "Jayce"),
                createChampion(127, "丽桑卓", "Lissandra"),
                createChampion(131, "黛安娜", "Diana"),
                createChampion(133, "奎因", "Quinn"),
                createChampion(134, "辛德拉", "Syndra"),
                createChampion(136, "奥瑞利安·索尔", "AurelionSol"),
                createChampion(141, "凯隐", "Kayn"),
                createChampion(142, "佐伊", "Zoe"),
                createChampion(143, "婕拉", "Zyra"),
                createChampion(145, "卡莎", "Kaisa"),
                createChampion(147, "塞拉斯", "Sylas"),
                createChampion(150, "纳尔", "Gnar"),
                createChampion(154, "扎克", "Zac"),
                createChampion(157, "亚索", "Yasuo"),
                createChampion(161, "维克兹", "Velkoz"),
                createChampion(163, "塔莉垭", "Taliyah"),
                createChampion(164, "卡蜜尔", "Camille"),
                createChampion(166, "永恩", "Yone"),
                createChampion(200, "贝蕾亚", "Briar"),
                createChampion(221, "泽丽", "Zeri"),
                createChampion(222, "金克丝", "Jinx"),
                createChampion(223, "塔姆", "TahmKench"),
                createChampion(236, "卢锡安", "Lucian"),
                createChampion(238, "劫", "Zed"),
                createChampion(240, "克烈", "Kled"),
                createChampion(245, "艾克", "Ekko"),
                createChampion(246, "莉莉娅", "Lillia"),
                createChampion(254, "蔚", "Vi"),
                createChampion(266, "亚托克斯", "Aatrox"),
                createChampion(267, "娜美", "Nami"),
                createChampion(268, "阿兹尔", "Azir"),
                createChampion(350, "悠米", "Yuumi"),
                createChampion(412, "锤石", "Thresh"),
                createChampion(420, "俄洛伊", "Illaoi"),
                createChampion(421, "雷克塞", "RekSai"),
                createChampion(427, "艾翁", "Ivern"),
                createChampion(429, "卡莉斯塔", "Kalista"),
                createChampion(432, "巴德", "Bard"),
                createChampion(497, "洛", "Rakan"),
                createChampion(498, "霞", "Xayah"),
                createChampion(516, "奥恩", "Ornn"),
                createChampion(517, "塞恩", "Sion"),
                createChampion(518, "诺娃", "Neeko"),
                createChampion(523, "阿菲利乌斯", "Aphelios"),
                createChampion(526, "芮尔", "Rell"),
                createChampion(555, "派克", "Pyke"),
                createChampion(711, "薇古丝", "Vex"),
                createChampion(777, "永恩", "Yone"),
                createChampion(875, "瑟提", "Sett"),
                createChampion(876, "莉莉娅", "Lillia"),
                createChampion(877, "永恩", "Yone"),
                createChampion(895, "尼菈", "Nilah"),
                createChampion(902, "奎桑提", "KSante"),
                createChampion(950, "米利欧", "Milio"),
                createChampion(951, "纳亚菲莉", "Naafiri")
        );
        championCache.putAll(builtIn);
    }

    private Map.Entry<Long, Champion> createChampion(long id, String name, String alias) {
        return Map.entry(id, new Champion(id, name, alias));
    }

    /**
     * 获取所有英雄选项
     */
    public List<ChampionOption> getChampionOptions() {
        return championCache.values().stream()
                .filter(c -> c.id > 0 && !c.name.contains("末日人机"))
                .map(c -> new ChampionOption(c.id, c.name, c.name, c.alias))
                .sorted(Comparator.comparing(ChampionOption::label))
                .toList();
    }

    /**
     * 获取英雄名称
     */
    public String getChampionName(long id) {
        Champion champion = championCache.get(id);
        return champion != null ? champion.name : "未知英雄";
    }

    /**
     * 获取装备图标路径
     */
    public String getItemIconPath(long id) {
        return itemIconPathCache.get(id);
    }

    /**
     * 获取召唤师技能图标路径
     */
    public String getSpellIconPath(long id) {
        return spellIconPathCache.get(id);
    }

    /**
     * 加载海克斯强化列表 (cherry-augments)
     */
    private void loadAugments() {
        try {
            CherryAugment[] augments = lcuHttpClient.get("lol-game-data/assets/v1/cherry-augments.json", CherryAugment[].class);
            if (augments != null) {
                for (CherryAugment augment : augments) {
                    if (augment.id > 0 && augment.augmentSmallIconPath != null && !augment.augmentSmallIconPath.isEmpty()) {
                        augmentIconPathCache.put(augment.id, augment.augmentSmallIconPath);
                        // 缓存稀有度
                        if (augment.rarity != null && !augment.rarity.isEmpty()) {
                            augmentRarityCache.put(augment.id, augment.rarity);
                        }
                    }
                }
            }
            log.info("海克斯强化加载完成: {}, 稀有度: {}", augmentIconPathCache.size(), augmentRarityCache.size());
        } catch (Exception e) {
            log.warn("加载海克斯强化失败: {}", e.getMessage());
        }
    }

    /**
     * 获取海克斯强化图标路径
     */
    public String getAugmentIconPath(long id) {
        String path = augmentIconPathCache.get(id);
        if (path != null && !path.isEmpty()) {
            return path;
        }
        // 如果缓存中没有，尝试使用默认路径格式
        return String.format("/lol-game-data/assets/v1/augments/%d.png", id);
    }

    /**
     * 获取海克斯强化稀有度
     */
    public String getAugmentRarity(long id) {
        return augmentRarityCache.getOrDefault(id, "");
    }

    // ========== 内部模型 ==========

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Champion {
        @JsonProperty("id")
        private long id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("alias")
        private String alias;

        public Champion() {
        }

        public Champion(long id, String name, String alias) {
            this.id = id;
            this.name = name;
            this.alias = alias;
        }
    }

    public record ChampionOption(long value, String label, String realName, String nickname) {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("id")
        private long id;

        @JsonProperty("iconPath")
        private String iconPath;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Spell {
        @JsonProperty("id")
        private long id;

        @JsonProperty("iconPath")
        private String iconPath;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Augment {
        @JsonProperty("id")
        private long id;

        @JsonProperty("iconPath")
        private String iconPath;

        @JsonProperty("name")
        private String name;

        @JsonProperty("rarity")
        private String rarity;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CherryAugment {
        @JsonProperty("id")
        private long id;

        @JsonProperty("iconPath")
        private String iconPath;

        @JsonProperty("augmentSmallIconPath")
        private String augmentSmallIconPath;

        @JsonProperty("nameTRA")
        private String nameTra;

        @JsonProperty("descriptionTRA")
        private String descriptionTra;

        @JsonProperty("rarity")
        private String rarity;
    }
}
