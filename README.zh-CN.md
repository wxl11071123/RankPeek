# RankPeek

RankPeek 是一个英雄联盟桌面侦查工具。这个仓库是公开源码版，重点保留本地客户端集成、战绩、玩家标签、OP.GG 辅助、本地缓存和 Electron 桌面壳。

## 开源范围

已包含：

- 连接 League Client / Riot 客户端数据的本地后端。
- Electron + Vue 桌面前端。
- 战绩查询、召唤师查询、对局侦查、玩家标签和自动化界面。
- 公开功能需要的本地缓存和数据库代码。
- OP.GG 英雄辅助窗口和游戏资产渲染工具。

不包含：

- 私有 AI 功能和提示词。
- 私有 RP 指数实现。
- 托管/云端服务代码、计费/积分系统、生产部署密钥和私有运维文档。

公开版会直接移除这些私有模块，不提供加密实现或占位实现。

## 仓库结构

```text
rankpeek-backend/      Spring Boot 本地后端
rankpeek-frontend/     Electron + Vue 桌面应用
docs/                  公开构建与 native-image 文档
scripts/               公开维护脚本
```

## 环境要求

- Windows 10/11
- Java 17+
- Maven 3.9+
- Node.js 20+
- npm 10+

## 本地开发

后端：

```bash
cd rankpeek-backend
mvn test
mvn spring-boot:run
```

前端：

```bash
cd rankpeek-frontend
npm install
npm run build
```

Electron 开发：

```bash
cd rankpeek-frontend
npm run electron:dev
```

## 打包

Electron 打包默认需要后端可执行文件位于：

```text
rankpeek-backend/target/rankpeek-native.exe
```

native-image 构建说明见 `docs/`。

## License

见 `LICENSE`。
