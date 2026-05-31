# RankPeek

[English README](README.md)

RankPeek 是一款面向《英雄联盟》的 Windows 桌面工具。它读取本地 League Client 数据，维护本地战绩缓存，并结合 RankPeek 云端服务提供账号、点数、OP.GG 风格英雄数据和 AI 分析能力。

当前项目结构是：

- `rankpeek-frontend`：Electron + Vue 3 桌面客户端。
- `rankpeek-backend`：本地 Windows 代理，运行在 `127.0.0.1:8080`，负责 LCU、SGP、资产、战绩和本地缓存链路。
- `rankpeek-server`：云端服务。前端默认使用 `https://api.rankpeek.cn`。除非正在开发云端接口，否则不要在本地启动它。

## 当前功能

### 桌面侦察

- 首页显示当前账号、段位、近期状态和本地连接状态。
- 对战信息页展示队友和对手。
- 根据游戏阶段自动切换页面，但不包含旧版自动排队、自动确认、自动 BP 功能。
- 使用本地 SQLite 保存战绩、详情、AI 报告和缓存补全数据。

### 战绩与详情

- 我的战绩和战绩查询。
- 战绩详情懒加载，减少首屏等待。
- 对局总览、符文/出装、线图和 timeline 相关详情。
- 排位对局在 timeline 和完整对位数据可用时显示 RP 指数。

### RP 指数

RP 指数是 RankPeek 基于 timeline 计算的单局排位表现信号。

- 只用于单双排位和灵活排位。
- 输入包括经济、等级、CS、参团、死亡、关键资源和视野。
- 对局详情页展示 RP 曲线和终局 RP。
- AI snapshot 只保留必要 RP 事实，避免把完整 timeline 原始数据全部发给模型。

### AI 分析

AI 功能通过 `rankpeek-server` 提供；当云端启用真实 AI provider 时，需要登录 RankPeek 账号，并按服务端点数规则计费。

- 赛前分析：分析当前大厅/队友/对手上下文。
- 赛后复盘：对单局进行客观复盘。
- 夸夸机：和赛后复盘共用 snapshot，但使用不同提示词和输出 schema。
- 电子教练：分析最近 20 局排位数据，重点看 RP、15 分钟对位经济、参团率、英雄池/位置样本和单局事实。
- DeepSeek token usage 等成本数据走服务端链路，后续可用于统计成本。

### OP.GG 窗口

- Electron 内置独立 OP.GG 风格英雄页面。
- 支持英雄榜单和英雄详情。
- 支持段位、模式、地区、位置和英雄筛选。
- 进入 OP.GG 页时自动跳转一次；对战大厅中只有用户自己选择英雄后才再次自动跳转。
- 数据通过云端 `rankpeek-server` 获取。

### RankPeek 账号与点数

- 登录、注册邮箱验证码、密码重置和 session refresh。
- 点数余额和流水接口。
- 云端真实 AI 请求需要鉴权，并由服务端记录点数扣除、退款和 token usage。

## 安全边界

RankPeek 不是自动化工具。旧版自动排队、自动接收、自动选人、自动禁用等 UI 路径已经不属于当前桌面应用。

任何控制 League Client 的功能都应视为高风险，需要单独评审。因客户端自动化导致的账号处罚、限制或其他后果，由使用者自行承担。

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

云端服务开发需要：

- Java 21
- Maven 3.9+
- 生产类运行需要 PostgreSQL；测试使用 H2

## 快速开始：桌面开发

普通客户端开发只需要本地 `rankpeek-backend` 和 Electron 前端。云端 server 已默认指向 `https://api.rankpeek.cn`。

### 1. 启动本地 backend 代理

```powershell
.\scripts\dev-backend.bat
```

它会启动 `rankpeek-backend` 到：

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

`electron:dev` 会构建 Electron main/preload，启动 Vite，并打开桌面壳。开发模式下，Electron 期望本地 backend 代理已经运行在 `8080` 端口。

## 云端 server 开发

只有在修改云端接口、AI streaming、账号、点数、OP.GG 代理、管理员功能或部署脚本时，才需要本地启动 `rankpeek-server`。

```powershell
cd rankpeek-server
mvn spring-boot:run
```

默认本地地址：

```text
http://localhost:18080
```

如果要让前端连接本地 server，而不是线上 `https://api.rankpeek.cn`：

```powershell
cd rankpeek-frontend
$env:VITE_RANKPEEK_SERVER_BASE_URL = "http://localhost:18080"
npm run electron:dev
```

更多云端服务说明见 [rankpeek-server/README.md](rankpeek-server/README.md)。

## 构建

### 前端 bundle

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

Electron 安装包会期望本地 backend 原生可执行文件存在于：

```text
rankpeek-backend/target/rankpeek-native.exe
```

### 完整 Windows 安装包脚本

```powershell
.\build.bat
```

该脚本会先构建 `rankpeek-backend` 原生二进制，再构建 Electron 安装包。它只面向 Windows，并且当前需要按本机环境调整 `GRAALVM_HOME`。

### 云端 server jar

```powershell
cd rankpeek-server
mvn -B -DskipTests package
```

CI 会构建并上传：

```text
rankpeek-server/target/rankpeek-server-0.1.0.jar
```

## 测试命令

前端测试使用 Node test：

```powershell
cd rankpeek-frontend
node --test src/renderer/services/rankpeekServerClient.test.ts
npm run build:renderer
```

本地 backend 测试：

```powershell
cd rankpeek-backend
mvn test
```

云端 server 测试：

```powershell
cd rankpeek-server
mvn test
```

旧自动化功能残留检查：

```powershell
node scripts/check-no-automation.mjs
```

## 项目结构

```text
rankpeek-frontend/              Electron + Vue 桌面客户端
rankpeek-backend/               本地 Windows 代理，负责 LCU、SGP、资产和缓存
rankpeek-server/                云端账号、点数、AI、OP.GG 数据、管理和部署服务
rankpeek-server/deploy/ubuntu/  生产部署脚本和模板
docs/                           规划、部署和产品说明
scripts/                        开发脚本和仓库守卫脚本
build.bat                       Windows 原生 backend + Electron 打包辅助脚本
```

## 数据与隐私边界

- 本地 backend 负责读取本机 League Client 和战绩相关来源。
- 云端 server 负责 RankPeek 账号、点数、AI 请求、OP.GG 英雄数据和管理员 API。
- 云端 server 不应接收 LCU token、SGP token、本地缓存数据库或不必要的私有原始对局 payload。
- AI 输入 snapshot 会尽量压缩成自然语言事实，减少成本，也避免发送原始大对象。

## 已知限制

- RankPeek 当前以 Windows 桌面体验为主。
- LCU 相关功能需要英雄联盟客户端正在运行。
- Riot/LCU/SGP 不暴露的数据，应用只能降级提示，不能绕过限制。
- RP 指数只支持 timeline 和完整对位数据可用的 420/440 排位对局。
- AI 功能依赖 RankPeek 云端服务、账号状态、点数和 provider 可用性。

## 许可证

项目基于 [MIT License](LICENSE) 发布。
