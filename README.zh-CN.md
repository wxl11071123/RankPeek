# RankPeek

[English README](README.md)

RankPeek 是一款面向《英雄联盟》的 Windows 桌面工具。它通过本地后端读取本机 League Client，维护本地缓存，并允许用户配置自己的 AI provider 来完成分析。

打包后的桌面应用采用本地优先架构：

- `rankpeek-frontend`：Electron + Vue 3 桌面客户端。
- `rankpeek-backend`：运行在 `127.0.0.1:8080` 的本地 Windows 服务，负责 LCU、SGP、资产、战绩、AI、OP.GG 数据、国服 meta 数据和本地成本流水。
- `rankpeek-cloudflare`：Cloudflare Worker API，负责官网反馈、公开公告和管理员公告后台。

## 当前功能

### 桌面侦察

- 首页显示当前账号、段位、近期状态和本地服务状态。
- 对战信息页展示队友和对手。
- 根据游戏阶段自动切换页面，但不包含旧版自动排队、自动确认、自动选择或自动禁用功能。
- 本地保存战绩、详情、AI 报告、OP.GG 缓存、国服 meta 缓存和成本记录。

### 战绩与 RP 指数

- 我的战绩和召唤师查询视图。
- 懒加载战绩详情，包含队伍总览、符文/出装、图表和 timeline 详情。
- 当 timeline 和完整对位数据可用时，为单双排和灵活排位生成 RP 指数。
- AI snapshot 使用紧凑的对局事实，而不是发送过大的原始 payload。

### 本地 AI 分析

- 赛前分析当前大厅和队伍上下文。
- 赛后复盘和夸夸模式。
- 电子教练分析近期排位数据。
- 在设置页配置用户自己的 AI provider：provider、base URL、模型、API key、开通页面链接、联网/深度思考开关和可选自填价格。
- 用户填写 Base URL + API key 后，会从 provider 的 `/models` 接口刷新模型候选；刷新失败时仍可手动填写模型。
- OpenAI-compatible 预设可用于 DeepSeek、Qwen、MiniMax、MiMo（小米）、GLM，以及自定义兼容服务。
- 本地记录 AI 运行历史、token usage、缓存命中/未命中和成本估算。

### OP.GG 与国服 Meta 数据

- Electron 内置独立 OP.GG 风格英雄页面。
- 支持英雄榜单、英雄详情、段位、模式、地区、位置和英雄筛选。
- OP.GG 数据由本地后端代理和缓存，避免 renderer CORS 问题，并集中处理限速和降级。
- 国服 meta、版本、LPL 和 prompt context 数据由本地后端存储和同步。

### 本地成本流水

- 当 provider 返回 token usage 时，AI 成本会自动本地记录。
- AI 成本单价由用户可选自填；留空时成本记为未知。
- 桌面应用不再需要 RankPeek 账号、注册、充值、点数余额或托管计费流程。

## 安全边界

RankPeek 不是自动化产品。旧版自动排队、自动接收、自动选择和自动禁用等 UI 路径不属于当前桌面应用。

任何控制 League Client 的功能都应视为高风险，需要单独评审。因客户端自动化导致的账号处罚、限制或其他后果由使用者自行承担。

## 环境要求

普通桌面开发需要：

- Windows 10 或 Windows 11
- 正在运行的英雄联盟客户端
- Node.js 18+
- Java 21
- Maven 3.9+

原生安装包构建还需要：

- GraalVM JDK 21
- Visual Studio Build Tools，包含 C++ 支持
- 在 `build.bat` 中配置正确的 `GRAALVM_HOME`

AI 分析需要：

- 用户自己提供的 OpenAI-compatible API key，或不需要 key 的 provider/test mode。

## 快速开始：桌面开发

普通桌面开发只需要本地 backend 和 Electron 前端。

### 1. 启动本地 Backend

```powershell
.\scripts\dev-backend.bat
```

它会启动 `rankpeek-backend`：

```text
http://127.0.0.1:8080
```

同时设置：

```text
RANKPEEK_LOCAL_DATA_ROOT=%LOCALAPPDATA%\RankPeek-dev
```

这样开发缓存不会污染打包版应用的数据目录。

### 2. 启动 Electron

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

`electron:dev` 会构建 Electron main/preload，启动 Vite，并打开桌面壳。开发模式下，Electron 期望本地 backend 已经运行在 `8080` 端口。

### 3. 配置 AI

打开桌面应用的设置页并配置 AI provider。API key 由本地 backend 存储在本地，不会发送给 RankPeek 云端服务。

## 构建

### 前端 Bundle

```powershell
cd rankpeek-frontend
npm install
npm run build
```

### Electron 安装包

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

Electron 安装包期望本地 backend 原生可执行文件位于：

```text
rankpeek-backend/target/rankpeek-native.exe
```

### 完整 Windows 安装包脚本

```powershell
.\build.bat
```

该脚本会运行仓库守卫，构建 `rankpeek-backend` 原生二进制，再构建 Electron 安装包。它只面向 Windows，并且需要按本机环境调整 `GRAALVM_HOME`。

## 测试命令

前端测试：

```powershell
cd rankpeek-frontend
node --test src/renderer/services/*.test.ts
npm run build:renderer
```

本地 backend 测试：

```powershell
cd rankpeek-backend
mvn test
```

仓库守卫：

```powershell
node scripts/check-no-automation.mjs
node scripts/check-no-cloud-server.mjs
```

## 项目结构

```text
rankpeek-frontend/              Electron + Vue 桌面客户端
rankpeek-backend/               本地 Windows 服务，负责 LCU、SGP、AI、OP.GG、国服 meta、成本和缓存
rankpeek-cloudflare/            Cloudflare Worker API，负责反馈和公告
rankpeek-website/               官网源码
docs/                           规划、部署和产品说明
scripts/                        开发脚本和仓库守卫脚本
build.bat                       Windows 原生 backend + Electron 打包辅助脚本
```

## 数据与隐私边界

- 本地 backend 负责读取本机 League Client 和战绩相关来源。
- AI provider 凭据由用户自己提供并在本地配置。
- AI snapshot 会尽量压缩成自然语言事实，降低成本，也避免发送原始大对象。
- AI 运行记录、token usage、OP.GG 缓存、国服 meta 缓存和成本记录都存储在本地 backend 数据库。
- 打包桌面应用不应要求注册账号、邮箱验证、充值、点数或历史云端服务。

## 已知限制

- RankPeek 当前以 Windows 桌面体验为主。
- LCU 相关功能需要英雄联盟客户端正在运行。
- Riot/LCU/SGP、OP.GG 或国服 meta 来源不提供的数据只能降级提示。
- RP 指数只支持 timeline 和完整对位数据可用的 420/440 排位对局。
- AI 质量、延迟、usage 元数据和成本可见性取决于用户选择的 provider。

## 许可证

项目基于 [MIT License](LICENSE) 发布。
