# RankPeek 项目任务文件：SGP 战绩迁移 + Akari-like 战绩页

## 0. 项目背景

仓库：`https://github.com/wxl11071123/rankpeek-rebuild`

RankPeek 当前是一个 Windows 优先的英雄联盟桌面对局侦察工具，大体结构为：

```text
rankpeek-frontend/   Electron + Vue 3 + TypeScript 桌面客户端
rankpeek-backend/    Spring Boot + Java 21 本地服务层
```

当前痛点：

1. 战绩查询主要依赖 LCU `lol-match-history` 接口。
2. LCU 战绩查询慢、不稳定，并且在部分状态下容易失败。
3. 用户希望改成类似 League Akari 的 SGP 查询链路。
4. 前端战绩页也希望逐步改造成 Akari-like 的展示方式。

目标不是直接复制 Akari 代码，而是参考其架构思路：

- 使用 SGP match-history-query 查询战绩。
- 使用 token 调用 SGP 接口。
- 把 SGP 返回数据映射为 RankPeek 现有模型，避免一次性重写所有前端页面。
- 后端优先完成数据源替换，再重做前端 UI。

注意：League Akari 是 GPL-3.0 项目。可以参考其公开接口思路和字段结构，但不要直接复制其源码实现到 RankPeek。

---

## 1. 总体改造原则

这不是一个适合一次性完成的大任务。请 Codex 按小 PR / 小提交方式推进：

1. 先后端，后前端。
2. 先抽象数据源，再接入 SGP。
3. 先保证旧 UI 能吃到 SGP 数据，再重做 Akari-like 战绩页。
4. 任何阶段都不能破坏现有 LCU fallback。
5. 每个阶段必须能编译、能测试、能回滚。
6. 不要把 SGP、缓存、DTO、前端视觉重构混在一个任务里。
7. 避免大范围重命名现有模型，除非确有必要。

---

## 2. 现有 RankPeek 相关链路

重点检查以下文件和模块：

```text
rankpeek-backend/src/main/java/io/rankpeek/service/MatchHistoryService.java
rankpeek-backend/src/main/java/io/rankpeek/service/SessionAnalysisService.java
rankpeek-backend/src/main/java/io/rankpeek/cache/JdbcMatchHistoryCacheRepository.java
rankpeek-backend/src/test/java/io/rankpeek/service/MatchHistoryServiceTest.java

rankpeek-frontend/src/renderer/api/httpClient.ts
rankpeek-frontend/src/renderer/types/api.ts
rankpeek-frontend/src/renderer/views/MatchHistoryView.vue
rankpeek-frontend/src/renderer/views/SummonerView.vue
rankpeek-frontend/src/renderer/components/HomeChart.vue
rankpeek-frontend/src/renderer/components/summoner/MatchDetailModal.vue
```

当前前端依赖的接口大致包括：

```text
GET /api/v1/summoner/matches/{puuid}
GET /api/v1/summoner/matches-filtered/{puuid}
GET /api/v1/summoner/game-detail/{gameId}
GET /api/v1/summoner/win-rate/{puuid}
GET /api/v1/summoner/ranked-win-rates/{puuid}
```

---

## 3. 推荐任务拆分

### PR 1：抽象 MatchHistoryProvider，保持 LCU 行为不变

目标：

- 不接入 SGP。
- 不改前端 UI。
- 只把 `MatchHistoryService` 中“从 LCU 拉数据”的逻辑抽象出去。

建议新增：

```text
rankpeek-backend/src/main/java/io/rankpeek/service/matchhistory/MatchHistoryProvider.java
rankpeek-backend/src/main/java/io/rankpeek/service/matchhistory/LcuMatchHistoryProvider.java
rankpeek-backend/src/main/java/io/rankpeek/service/matchhistory/MatchHistoryQueryOptions.java
rankpeek-backend/src/main/java/io/rankpeek/service/matchhistory/MatchHistorySource.java
```

`MatchHistoryProvider` 可以设计为：

```java
public interface MatchHistoryProvider {
    MatchHistorySource source();

    MatchHistoryFetchResult fetchMatchHistory(String puuid, MatchHistoryQueryOptions options);

    GameDetail fetchGameDetail(Long gameId, MatchHistoryQueryOptions options);

    boolean supports(MatchHistoryQueryOptions options);
}
```

`MatchHistoryQueryOptions` 建议包含：

```java
public record MatchHistoryQueryOptions(
    int begIndex,
    int endIndex,
    Integer queueId,
    Integer championId,
    Integer maxResults,
    boolean forceRefresh,
    MatchHistorySource preferredSource,
    String sgpServerId,
    String tag
) {}
```

验收标准：

```text
./mvnw test 或 mvn test 可通过。
前端原接口不变。
所有现有功能表现不变。
MatchHistoryService 仍负责缓存、fallback、recordStatus、winRate 编排。
LcuMatchHistoryProvider 负责 LCU 请求和 LCU JSON 解析。
```

---

### PR 2：加入 SGP server config 与状态接口

目标：

- 后端能知道当前区服对应哪个 SGP server。
- 暂时不请求战绩。

建议新增：

```text
rankpeek-backend/src/main/resources/sgp/league-servers.json
rankpeek-backend/src/main/java/io/rankpeek/sgp/SgpServerConfig.java
rankpeek-backend/src/main/java/io/rankpeek/sgp/SgpServerResolver.java
rankpeek-backend/src/main/java/io/rankpeek/controller/SgpController.java
```

建议接口：

```http
GET /api/v1/sgp/status
```

返回示例：

```json
{
  "supported": true,
  "platformId": "NA1",
  "sgpServerId": "NA1",
  "matchHistoryBaseUrl": "https://usw2-red.pp.sgp.pvp.net",
  "commonBaseUrl": "https://na-red.lol.sgp.pvp.net",
  "tokenReady": false
}
```

验收标准：

```text
LCU 已连接时可以解析当前区服。
LCU 未连接时返回明确状态，不抛 500。
配置文件格式有单元测试。
```

---

### PR 3：实现 SGP token service

目标：

- 从本地客户端状态中拿到 SGP 所需 token。
- token 缓存、失效、日志脱敏。

建议新增：

```text
SgpTokenService
SgpAuthState
```

需要处理：

```text
entitlements token
league session token
token ready 状态
token 缓存
token 失效
日志脱敏，严禁打印完整 token
```

验收标准：

```text
SGP status 能显示 token 是否 ready。
LCU 未连接时不反复刷日志。
日志中只允许出现 token 前几位 + ...。
```

---

### PR 4：实现 SgpHttpClient

目标：

- 用 Java/OkHttp 请求 SGP API。
- 只做底层 HTTP，不映射业务模型。

建议新增：

```text
SgpHttpClient
SgpApiException
SgpRequestOptions
SgpRawMatchHistoryResponse
SgpRawGameSummaryResponse
SgpRawGameDetailsResponse
```

首批方法：

```java
JsonNode getMatchHistorySummary(String puuid, int startIndex, int count, String tag, String sgpServerId);
JsonNode getGameSummary(Long gameId, String sgpServerId);
JsonNode getGameDetails(Long gameId, String sgpServerId);
```

SGP 路径参考：

```text
/match-history-query/v1/products/lol/player/{puuid}/SUMMARY
/match-history-query/v1/products/lol/{REGION}_{gameId}/SUMMARY
/match-history-query/v1/products/lol/{REGION}_{gameId}/DETAILS
```

验收标准：

```text
URL、headers、query params 有单元测试。
Authorization: Bearer {entitlementsToken} 正确注入。
超时、401、403、404、5xx 都有明确错误。
不影响 LCU 查询。
```

---

### PR 5：实现 SGP 到 RankPeek 模型映射

目标：

- 把 SGP summary/details 映射成现有 `MatchHistory` 和 `GameDetail`。
- 第一版尽量不改前端 DTO。

建议新增：

```text
SgpMatchHistoryMapper
SgpGameDetailMapper
SgpParticipantMapper
```

必须映射字段：

```text
gameId
queueId
queueName
gameMode
gameCreation
gameDuration
participantIdentities
participants
player.puuid
player.gameName
player.tagLine
player.summonerName
participant.championId
participant.teamId
participant.spell1Id
participant.spell2Id
participant.stats.win
participant.stats.kills/deaths/assists
participant.stats.goldEarned
participant.stats.totalDamageDealtToChampions
participant.stats.totalDamageTaken
participant.stats.totalMinionsKilled
participant.stats.neutralMinionsKilled
participant.stats.item0-item6
participant.stats.perk0
lane / role / teamPosition if present
```

验收标准：

```text
使用脱敏 SGP JSON fixture 做 mapper 单元测试。
胜负、英雄、KDA、装备、队伍名单正确。
MatchHistoryView 不改 UI 也能显示映射结果。
MatchDetailModal 能打开。
```

---

### PR 6：实现 SgpMatchHistoryProvider

目标：

- 后端正式支持 SGP 查询。
- 保留 LCU fallback。

建议新增：

```text
SgpMatchHistoryProvider implements MatchHistoryProvider
```

配置项：

```properties
rankpeek.match-history.provider=auto
```

取值：

```text
lcu  = 只用 LCU
sgp  = 只用 SGP，失败就报错
auto = SGP 优先，失败后 LCU fallback，再失败用 DB cache
```

缓存 key 需要考虑：

```text
source
puuid
sgpServerId
tag
page/start/count
schemaVersion
```

验收标准：

```text
SGP 成功时不再调用 LCU match-history。
auto 模式下 SGP 失败不会打空页面。
forceRefresh 能刷新 SGP。
旧缓存仍能兜底。
```

---

### PR 7：新增 matches-page API

目标：

- 给新前端一个更适合 SGP 和分页的接口。
- 旧接口继续保留。

新增接口：

```http
GET /api/v1/summoner/matches-page/{puuid}
```

参数：

```text
page
pageSize
tag
queueId
championId
source=lcu|sgp|auto
forceRefresh
```

返回：

```ts
interface MatchHistoryPage {
  games: MatchHistory[]
  page: number
  pageSize: number
  hasNext: boolean
  source: 'LCU' | 'SGP' | 'CACHE'
  sgpServerId?: string
  tag?: string
  recordStatus: 'NORMAL' | 'PRIVATE' | 'EMPTY' | 'ERROR'
  warnings?: string[]
}
```

验收标准：

```text
旧接口不坏。
新接口可以驱动战绩页分页。
返回 source/status/warnings，方便前端展示和调试。
```

---

### PR 8：前端 API/types 切换到 matches-page，UI 暂不重做

目标：

- 前端先使用新 API。
- 页面样式基本不变。

修改：

```text
rankpeek-frontend/src/renderer/types/api.ts
rankpeek-frontend/src/renderer/api/httpClient.ts
rankpeek-frontend/src/renderer/views/MatchHistoryView.vue
```

新增：

```ts
getMatchHistoryPage()
getSgpStatus()
MatchHistoryPage
MatchHistorySource
SgpStatus
SgpTagOption
```

验收标准：

```text
原战绩页可正常加载。
页面显示数据源：SGP / LCU / CACHE。
分页、刷新、筛选可用。
SGP 失败时有明确提示。
```

---

### PR 9：重做 Akari-like 战绩页

目标：

- 在数据源稳定后，再重构 UI。
- 保留 RankPeek 自己的暗色科技风，不照搬 Akari 视觉。

建议拆组件：

```text
MatchHistoryPlayerHeader.vue
MatchHistoryToolbar.vue
MatchHistoryStatsPanel.vue
MatchHistoryCard.vue
MatchHistoryParticipantList.vue
MatchHistoryDetailDrawer.vue
RecentlyPlayedPanel.vue
SgpTagSelect.vue
```

布局建议：

```text
顶部：玩家头像、名称、等级、单双排/灵活段位、数据源状态

左侧：分页、pageSize、SGP tag、统计面板
  - 近 N 场胜率
  - 平均 KDA
  - 平均参团率
  - 平均伤害占比
  - 平均承伤占比
  - 平均经济占比
  - 平均补刀占比
  - 常用英雄
  - 最近同队 / 最近对手

右侧：战绩卡片列表
  - 胜负色块
  - 英雄头像
  - 召唤师技能
  - 装备
  - KDA
  - CS / min
  - 伤害、经济、视野
  - 队伍名单
  - 点击展开详情
```

验收标准：

```text
Akari-like，但不照抄。
RankPeek 暗色视觉统一。
小窗口不崩。
分页、刷新、tag 筛选、详情展开都可用。
```

---

### PR 10：迁移关联功能

目标：

- 所有依赖战绩的功能统一使用 provider。

检查：

```text
HomeChart.vue
SummonerView.vue
MatchDetailModal.vue
UserTagService
SessionAnalysisService
MatchHistoryPrewarmService
MatchHistoryRefreshService
getRankedWinRates
getWinRate
```

验收标准：

```text
首页趋势图不再强依赖慢 LCU 战绩。
用户标签统计不重复拉取 LCU。
对局侦察不被 SGP 改造破坏。
预热逻辑不会触发大量 LCU 请求。
```

---

## 4. 推荐先给 Codex 的第一条提示词

建议先只做 PR 1，不要让 Codex 一次性完成全部迁移。

```markdown
你正在修改仓库 `wxl11071123/rankpeek-rebuild`。

## 背景

RankPeek 当前的战绩查询主要集中在后端 `rankpeek-backend/src/main/java/io/rankpeek/service/MatchHistoryService.java`。我后续想把战绩查询从 LCU `lol-match-history` 迁移到 SGP，但这次任务不要接入 SGP，也不要改前端 UI。

## 本次任务：只做战绩数据源抽象，保持行为完全不变

请完成第一阶段重构：

1. 新增 `MatchHistoryProvider` 抽象接口。
2. 新增 `LcuMatchHistoryProvider`，把当前 `MatchHistoryService` 里直接请求 LCU 战绩列表和单局详情的逻辑迁移进去。
3. 新增 `MatchHistoryQueryOptions`，用于承载 begIndex、endIndex、queueId、championId、maxResults、forceRefresh、preferredSource、sgpServerId、tag 等查询参数。
4. 新增 `MatchHistorySource` 枚举，先包含 `LCU`，可以预留 `SGP`、`AUTO`、`CACHE`，但本次不要实现 SGP。
5. `MatchHistoryService` 保持为编排层，继续负责：
   - 内存缓存。
   - DB 缓存 fallback。
   - recordStatus 解析。
   - winRate / rankedWinRates 统计。
   - filtered match history。
   - visible roster hydration。
6. 所有现有 REST API 行为必须保持兼容。
7. 前端文件不要改，除非编译必须。

## 重要约束

- 不要接入 SGP。
- 不要改 UI。
- 不要删除 LCU fallback。
- 不要大范围重命名现有 model。
- 不要改变 `/api/v1/summoner/matches/{puuid}`、`/matches-filtered/{puuid}`、`/game-detail/{gameId}` 的响应结构。
- 每个改动尽量小而清晰。
- 保留现有中文日志风格。

## 建议检查的文件

- `rankpeek-backend/src/main/java/io/rankpeek/service/MatchHistoryService.java`
- `rankpeek-backend/src/main/java/io/rankpeek/model/MatchHistory.java`
- `rankpeek-backend/src/main/java/io/rankpeek/model/GameDetail.java`
- `rankpeek-backend/src/main/java/io/rankpeek/model/MatchHistoryFetchResult.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cache/MatchHistoryCacheRepository.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cache/JdbcMatchHistoryCacheRepository.java`
- `rankpeek-backend/src/test/java/io/rankpeek/service/MatchHistoryServiceTest.java`

## 验收标准

1. 后端测试能通过：
   ```bash
   cd rankpeek-backend
   mvn test
   ```
2. 如果仓库没有 wrapper，就用系统 Maven。
3. 现有战绩页、召唤师页、首页趋势图不需要改代码也能继续工作。
4. `MatchHistoryService` 中不应该再直接拼接 LCU match-history URI；这部分应在 `LcuMatchHistoryProvider` 中。
5. 保留原来的异常处理、缓存写入、fallback 行为。
6. 最后请输出：
   - 修改文件列表。
   - 关键设计说明。
   - 测试结果。
   - 如果有未解决风险，请明确列出。
```

---

## 5. 第二条 Codex 提示词：SGP 配置与状态接口

等 PR 1 合并或确认没问题后，再给 Codex：

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：加入 SGP server config 和 SGP 状态接口，不请求战绩

上一阶段已经抽象了 `MatchHistoryProvider` 和 `LcuMatchHistoryProvider`。本次不要接入 SGP 查询，只做配置读取和状态展示。

请新增：

1. `rankpeek-backend/src/main/resources/sgp/league-servers.json`
2. `SgpServerConfig`
3. `SgpServerResolver`
4. `SgpStatus`
5. `SgpController`

新增接口：

```http
GET /api/v1/sgp/status
```

返回字段建议：

```json
{
  "supported": true,
  "platformId": "NA1",
  "sgpServerId": "NA1",
  "matchHistorySupported": true,
  "commonSupported": true,
  "matchHistoryBaseUrl": "...",
  "commonBaseUrl": "...",
  "tokenReady": false,
  "message": "..."
}
```

要求：

- 不要打印敏感 token。
- LCU 未连接时不能 500。
- 暂时不要请求 SGP API。
- 不要改前端 UI。
- 添加基础单元测试，验证配置解析、未知区服、空配置。

验收：

```bash
cd rankpeek-backend
mvn test
```

最后输出修改文件、设计说明和测试结果。
```

---

## 6. 第三条 Codex 提示词：SGP HTTP Client

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：实现 SGP HTTP Client，但不接入业务 Provider

请新增 Java 后端 SGP HTTP client，用 OkHttp 和现有 ObjectMapper 实现底层请求。

新增类建议：

- `SgpHttpClient`
- `SgpApiException`
- `SgpRequestOptions`
- 必要的 raw response DTO，或者先用 `JsonNode`

实现方法：

```java
JsonNode getMatchHistorySummary(String puuid, int startIndex, int count, String tag, String sgpServerId);
JsonNode getGameSummary(Long gameId, String sgpServerId);
JsonNode getGameDetails(Long gameId, String sgpServerId);
```

路径：

```text
/match-history-query/v1/products/lol/player/{puuid}/SUMMARY
/match-history-query/v1/products/lol/{REGION}_{gameId}/SUMMARY
/match-history-query/v1/products/lol/{REGION}_{gameId}/DETAILS
```

约束：

- 使用 SGP matchHistory baseURL。
- 使用 entitlements token：`Authorization: Bearer {token}`。
- token 不允许完整打印。
- 要处理 timeout、401、403、404、5xx。
- 不要改前端。
- 不要接入 `MatchHistoryService`，只完成底层 client 和测试。

验收：

- 单元测试覆盖 URL、headers、query params。
- `mvn test` 通过。
- 输出修改文件、测试结果、未解决风险。
```

---

## 7. 第四条 Codex 提示词：SGP Mapper

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：实现 SGP JSON 到 RankPeek 现有模型的 mapper

请新增 mapper，把 SGP summary/details 映射成现有 `MatchHistory` 和 `GameDetail`，但不要改前端 UI。

新增类建议：

- `SgpMatchHistoryMapper`
- `SgpGameDetailMapper`
- `SgpParticipantMapper`

需要映射字段：

- gameId
- queueId
- gameMode
- gameCreation
- gameDuration
- participantIdentities
- participants
- player.puuid
- player.gameName
- player.tagLine
- player.summonerName
- participant.championId
- participant.teamId
- participant.spell1Id / spell2Id
- participant.stats.win
- kills / deaths / assists
- goldEarned
- totalDamageDealtToChampions
- totalDamageTaken
- totalMinionsKilled
- neutralMinionsKilled
- item0-item6
- perk0
- lane / role / teamPosition if present

要求：

- 使用脱敏 JSON fixture 做单元测试。
- 不要直接复制 League Akari 源码。
- 允许参考字段映射思路，但要重新实现。
- 尽量保持 RankPeek 现有 DTO 不变。

验收：

```bash
cd rankpeek-backend
mvn test
```

最后输出修改文件、映射策略和测试结果。
```

---

## 8. 第五条 Codex 提示词：接入 SgpMatchHistoryProvider

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：实现 SgpMatchHistoryProvider，并加入 source=auto/sgp/lcu 选择

前置条件：

- 已有 `MatchHistoryProvider`。
- 已有 `LcuMatchHistoryProvider`。
- 已有 `SgpHttpClient`。
- 已有 SGP mapper。

请实现：

1. `SgpMatchHistoryProvider implements MatchHistoryProvider`
2. 配置项：`rankpeek.match-history.provider=auto|sgp|lcu`
3. `auto` 模式：SGP 优先，失败后 LCU fallback，再失败使用 DB cache。
4. `sgp` 模式：只用 SGP。
5. `lcu` 模式：只用 LCU。
6. 缓存 key 需要包含 source / puuid / sgpServerId / tag / schemaVersion。
7. `forceRefresh` 能绕过内存缓存重新请求。

要求：

- 旧 REST API 不变。
- 前端暂时不改。
- 保留原有 DB fallback。
- 日志要能看出当前使用了 SGP、LCU 还是 CACHE。
- SGP 失败时不要吞掉关键信息，但也不要把 token 打印出来。

验收：

```bash
cd rankpeek-backend
mvn test
```

最后输出修改文件、fallback 策略、测试结果和风险点。
```

---

## 9. 第六条 Codex 提示词：新增 matches-page API

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：新增更适合 SGP 的分页战绩接口

新增接口：

```http
GET /api/v1/summoner/matches-page/{puuid}
```

参数：

- page
- pageSize
- tag
- queueId
- championId
- source=lcu|sgp|auto
- forceRefresh

返回 DTO：

```ts
interface MatchHistoryPage {
  games: MatchHistory[]
  page: number
  pageSize: number
  hasNext: boolean
  source: 'LCU' | 'SGP' | 'CACHE'
  sgpServerId?: string
  tag?: string
  recordStatus: 'NORMAL' | 'PRIVATE' | 'EMPTY' | 'ERROR'
  warnings?: string[]
}
```

要求：

- 旧接口继续保留。
- 不要改 UI。
- Controller 参数校验要合理。
- `page` 最小为 1。
- `pageSize` 限制最大值，例如 100。
- `source` 非法时返回明确错误。

验收：

```bash
cd rankpeek-backend
mvn test
```

最后输出接口说明、修改文件、测试结果。
```

---

## 10. 第七条 Codex 提示词：前端 API/types 先接入，不重做 UI

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：前端接入 matches-page API，但暂时不重做战绩页 UI

修改：

- `rankpeek-frontend/src/renderer/types/api.ts`
- `rankpeek-frontend/src/renderer/api/httpClient.ts`
- `rankpeek-frontend/src/renderer/views/MatchHistoryView.vue`

新增：

- `MatchHistoryPage`
- `MatchHistorySource`
- `SgpStatus`
- `getMatchHistoryPage()`
- `getSgpStatus()`

要求：

- 当前页面布局基本不变。
- 使用新接口加载战绩。
- 页面上添加很小的数据源提示：SGP / LCU / CACHE。
- SGP 失败时显示明确错误或 warning。
- 分页、刷新、筛选继续可用。
- 不要重写 MatchHistoryCard，不要做 Akari-like UI。

验收：

```bash
cd rankpeek-frontend
npm install
npm run typecheck
npm run build
```

如果项目没有 typecheck 脚本，请运行现有可用的 lint/build 脚本。

最后输出修改文件、测试结果和剩余问题。
```

---

## 11. 第八条 Codex 提示词：Akari-like 战绩页 UI 重构

```markdown
继续修改 `wxl11071123/rankpeek-rebuild`。

## 本次任务：重构 MatchHistoryView 为 Akari-like 战绩页

前置条件：

- 后端 SGP 数据源已经稳定。
- 前端已经能通过 `getMatchHistoryPage()` 加载战绩。

目标：

- 参考 League Akari 的信息架构，但保留 RankPeek 暗色科技风。
- 不要照搬 Akari 样式或源码。

建议组件拆分：

- `MatchHistoryPlayerHeader.vue`
- `MatchHistoryToolbar.vue`
- `MatchHistoryStatsPanel.vue`
- `MatchHistoryCard.vue`
- `MatchHistoryParticipantList.vue`
- `MatchHistoryDetailDrawer.vue`
- `RecentlyPlayedPanel.vue`
- `SgpTagSelect.vue`

页面结构：

顶部：

- 玩家头像
- 玩家名 + tagLine
- 等级
- 单双排 / 灵活段位
- 数据源状态
- 刷新按钮

左侧：

- 页码
- pageSize
- SGP tag 筛选：all, q_420, q_430, q_440, q_450, q_480, q_1700, q_490, q_1900, q_900, q_2300
- 近 N 场统计
- 常用英雄
- 最近同队 / 最近对手

右侧：

- 战绩卡片列表
- 胜负色块
- 英雄头像
- 召唤师技能
- 装备
- KDA
- CS/min
- 伤害、经济、视野摘要
- 双方队伍名单
- 点击展开详情

要求：

- 适配小窗口。
- 动效克制，不要影响性能。
- 不要破坏 `MatchDetailModal` 的现有能力，必要时逐步替换为 drawer。
- 不要一次性删除旧组件，除非确认无引用。
- 所有文案接入现有 i18n 体系。

验收：

```bash
cd rankpeek-frontend
npm run build
```

如果有 typecheck/lint 脚本，也一并运行。

最后输出修改文件、UI 结构说明、测试结果、已知问题。
```

---

## 12. 测试清单

后续每个阶段至少检查：

```text
LCU 未连接
SGP token 缺失
SGP token 过期
SGP 区服不支持
SGP 401 / 403 / 404 / 5xx
玩家隐藏战绩
玩家没有战绩
单双排 q_420
匹配 q_430
灵活 q_440
大乱斗 q_450
第一页 / 下一页 / 上一页
pageSize 10 / 20 / 50
详情弹窗
缓存命中
重启后缓存读取
SGP 失败 fallback 到 LCU
LCU 失败 fallback 到 DB
```

---

## 13. 性能目标

```text
首屏 SGP 战绩冷加载 < 3 秒
缓存命中 < 500 ms
详情缓存命中 < 800 ms
翻页不阻塞整个 UI
每页不要无脑拉 50 个详情
```

---

## 14. 推荐执行顺序

```text
1. 先给 Codex 执行 PR 1：抽象 MatchHistoryProvider。
2. PR 1 合并或手动确认无问题后，再执行 PR 2。
3. 每次只执行一个 PR 级任务。
4. 不要让多个 Codex agent 同时改同一个文件，例如 MatchHistoryService 或 MatchHistoryView。
5. 如果要并行，只能并行互不冲突的任务：
   - 一个 agent 做后端 SGP config。
   - 一个 agent 做前端 UI 草图。
   - 一个 agent 做测试 fixture。
   但真正落地前要人工合并。
```
