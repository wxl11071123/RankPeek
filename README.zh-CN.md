# RankPeek

[English README](README.md)

RankPeek 是一款面向《英雄联盟》的桌面对局侦察工具，核心目标是在游戏开始前，帮你更快看清这把对局的风险、节奏和玩家状态。

它通过读取本地 League Client（LCU）数据，提供召唤师查询、近期战绩浏览、队友与对手标签、实时会话侦察，以及带有锐评风格的 AI 分析能力。

## RankPeek 能做什么

### 对局侦察

- 在选人阶段查看当前房间玩家信息
- 展示单排 / 灵活组排段位
- 用更紧凑的标签快速标记强势点和风险点
- 明确区分隐藏战绩、暂无战绩和拉取失败

### 战绩浏览

- 查看最近对局与队友 / 对手名单
- 战绩页优先展示轻量摘要，减少等待时间
- 点开详情时再懒加载完整对局数据
- 在战绩页直接看到玩家摘要标签

### 玩家画像

- 查看召唤师近期表现趋势
- 标记最佳队友和难打对手
- 生成适合快速扫读的摘要标签
- 在标签分析页查看更完整的标签结果

### AI 分析

- 整局复盘
- 单人复盘
- 房间 / 会话分析
- 先给结论，再给证据
- 语气更锐利，但结论必须绑定真实数据

### 自动化辅助

- 自动接受对局
- 自动开始匹配
- 自动选人 / 禁用辅助
- 通过设置项统一管理自动化开关

## 工作方式

RankPeek 是一套本地运行的 Windows 桌面应用，由以下部分组成：

- `Electron + Vue 3 + TypeScript`：桌面前端界面
- `Spring Boot + Java 21`：本地后端服务
- `LCU HTTP + WebSocket`：读取英雄联盟客户端数据
- 兼容 Chat Completions 的 AI 接口：提供可选的 AI 分析能力

整个核心链路以 LCU 为主，不依赖 Riot 公网 API Key 才能完成基础侦察流程。但这也意味着，客户端本身不暴露的数据，RankPeek 只能做清晰降级，不能强行绕过。

## 环境要求

- Windows 10 / Windows 11
- 正在运行的英雄联盟客户端
- Node.js 18+
- Java 21
- Maven 3.9+

如果你需要打包原生后端，还需要：

- GraalVM JDK 21
- Visual Studio Build Tools（含 C++ 组件）

## 快速开始

### 1. 启动后端

```powershell
cd rankpeek-backend
mvn spring-boot:run
```

默认地址：

```text
http://127.0.0.1:8080
```

### 2. 启动桌面端开发模式

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

这会同时启动 Vite 开发服务和 Electron 外壳，适合实时调 UI。

### 3. 可选：启用 AI 分析

在启动后端前设置环境变量：

```powershell
$env:AI_API_KEY="<your-api-key>"
$env:AI_API_ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:AI_MODEL="qwen-plus"
```

其中 `AI_API_KEY` 是必须的，其他两项可以按需覆盖。

## 构建发布

### 一键打包

```powershell
.\build.bat
```

默认产物：

- `rankpeek-backend/target/rankpeek-native.exe`
- `rankpeek-frontend/release/RankPeek Setup <version>.exe`
- `rankpeek-frontend/release/win-unpacked/`

### 只构建桌面端

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

## 项目结构

```text
rankpeek-frontend/   Electron + Vue 桌面客户端
rankpeek-backend/    Spring Boot 本地服务层
build.bat            Windows 一键构建脚本
docs/                构建与设计文档
```

## 当前特点

- 战绩和会话页优先走摘要标签，减少不必要的重型请求
- 对局详情采用懒加载，首屏更快
- 隐藏战绩会被当成明确状态处理，不再和“查不到数据”混在一起
- AI 分析允许更尖锐的表达，但不会脱离数据乱编

## 已知限制

- RankPeek 的核心数据链路是 LCU-only
- 如果英雄联盟客户端没有暴露某些私密数据，应用只能做清晰提示，不能突破限制
- 当前以 Windows 桌面端体验为主

## 许可证

项目基于 [MIT License](LICENSE) 发布。
