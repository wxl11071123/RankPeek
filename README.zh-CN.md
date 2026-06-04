# RankPeek

[English](README.md)

RankPeek 是一款面向《英雄联盟》玩家的 Windows 桌面工具，提供实时战绩查询、对局分析和数据追踪功能。

## 功能特性

### 实时对局侦察

- **当前账号信息**：首页展示当前登录账号、段位、近期状态
- **对局信息**：英雄选择阶段显示队友和对手的详细信息
- **自动导航**：根据游戏阶段自动切换页面（大厅、匹配、英雄选择、游戏中）
- **本地数据持久化**：战绩、详情、OP.GG 缓存等数据本地保存

### 战绩查询

- **我的战绩**：查看自己的历史对局记录
- **召唤师查询**：搜索其他玩家的战绩信息
- **战绩详情**：懒加载的详情面板，包含队伍总览、符文出装、数据图表
- **筛选功能**：按模式、英雄、时间段筛选战绩

### OP.GG 数据

- **英雄榜单**：内置 OP.GG 风格的英雄强度排行
- **英雄详情**：查看英雄的胜率、选取率、Ban 率等数据
- **多维度筛选**：按段位、模式、地区、位置筛选数据
- **本地缓存**：数据本地代理缓存，避免重复请求

### 本地缓存系统

- **战绩缓存**：历史战绩本地存储，减少 API 请求
- **资产缓存**：英雄头像、物品图标等资源本地缓存
- **OP.GG 缓存**：OP.GG 数据本地同步，提升加载速度

## 下载安装

### 方式一：下载安装包

前往 [GitHub Releases](https://github.com/wxl11071123/rankpeek/releases) 下载最新版本的安装包。

### 方式二：从源码构建

参考下方「开发者指南」部分。

## 系统要求

- Windows 10 或 Windows 11
- 正在运行的英雄联盟客户端（LCU 相关功能需要）
- Node.js 18+（从源码构建时需要）
- Java 21（从源码构建时需要）
- Maven 3.9+（从源码构建时需要）

## 使用说明

1. 启动 RankPeek 桌面应用
2. 确保英雄联盟客户端正在运行
3. 应用会自动检测当前登录的账号
4. 在首页查看当前账号状态和近期战绩
5. 进入英雄选择阶段时，会自动显示队友和对手信息

---

# 开发者指南

以下内容面向希望参与开发或自行构建的开发者。

## 技术架构

RankPeek 采用本地优先架构，所有数据处理在本地完成：

```
┌─────────────────────────────────────────────────────────────┐
│                    RankPeek Desktop                          │
│                    (Electron + Vue 3)                        │
├─────────────────────────────────────────────────────────────┤
│                    Local Backend                             │
│              (Spring Boot on port 8080)                      │
├─────────────────────────────────────────────────────────────┤
│         ┌──────────┬──────────┬──────────┬──────────┐        │
│         │   LCU    │   SGP    │  Assets  │  Cache   │        │
│         └──────────┴──────────┴──────────┴──────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 技术栈 | 职责 |
|------|--------|------|
| `rankpeek-frontend` | Electron + Vue 3 + TypeScript | 桌面客户端 UI |
| `rankpeek-backend` | Java 21 + Spring Boot | 本地后端服务 |
| `rankpeek-cloudflare` | Cloudflare Workers | 官网 API（反馈、公告） |
| `rankpeek-website` | Vue 3 + Vite | 官网 |

### 后端服务模块

本地后端 (`rankpeek-backend`) 提供以下服务：

- **LCU 连接**：读取本地 League Client 数据
- **SGP 接口**：获取 Riot 服务数据
- **资产服务**：英雄、物品、符文等资源管理
- **战绩存储**：SQLite 本地数据库持久化
- **OP.GG 代理**：OP.GG 数据请求代理和缓存
- **国服数据**：国服 meta、版本、LPL 数据同步

## 项目结构

```
rankpeek-rebuild/
├── rankpeek-frontend/              # Electron + Vue 桌面客户端
│   ├── src/
│   │   ├── main/                   # Electron 主进程
│   │   ├── preload/                # Electron 预加载脚本
│   │   └── renderer/               # Vue 渲染进程
│   │       ├── components/         # Vue 组件
│   │       ├── views/              # 页面视图
│   │       ├── stores/             # Pinia 状态管理
│   │       ├── services/           # 业务服务
│   │       └── utils/              # 工具函数
│   └── public/                     # 静态资源
│       └── game-assets/            # 游戏资源（本地缓存）
│
├── rankpeek-backend/               # 本地后端服务
│   └── src/main/java/com/rankpeek/
│       ├── controller/             # REST 控制器
│       ├── service/                # 业务服务
│       ├── model/                  # 数据模型
│       └── config/                 # 配置类
│
├── rankpeek-cloudflare/            # Cloudflare Worker
│   └── src/
│
├── rankpeek-website/               # 官网
│   └── src/
│
├── docs/                           # 文档
├── scripts/                        # 开发脚本
└── build.bat                       # Windows 打包脚本
```

## 开发环境搭建

### 前置要求

- Windows 10/11
- Node.js 18+
- Java 21（推荐 GraalVM）
- Maven 3.9+
- 正在运行的英雄联盟客户端（测试 LCU 功能时需要）

### 步骤 1：启动本地后端

```powershell
.\scripts\dev-backend.bat
```

这会启动后端服务在 `http://127.0.0.1:8080`，并设置开发数据目录为 `%LOCALAPPDATA%\RankPeek-dev`。

### 步骤 2：启动 Electron 前端

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

`electron:dev` 命令会：
1. 构建 Electron main/preload
2. 启动 Vite 开发服务器
3. 打开桌面应用窗口

### 步骤 3：验证开发环境

1. 确保后端服务运行在 8080 端口
2. 确保英雄联盟客户端正在运行（测试 LCU 功能）
3. 在 Electron 应用中查看首页是否显示账号信息

## 构建部署

### 构建前端 Bundle

```powershell
cd rankpeek-frontend
npm install
npm run build
```

### 构建 Electron 安装包

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

构建产物位于 `rankpeek-frontend/release/` 目录。

### 完整 Windows 打包

```powershell
.\build.bat
```

此脚本会：
1. 运行仓库守卫检查
2. 构建原生后端二进制（需要 GraalVM）
3. 构建 Electron 安装包

## 测试

### 前端测试

```powershell
cd rankpeek-frontend
node --test src/renderer/services/*.test.ts
npm run build:renderer
```

### 后端测试

```powershell
cd rankpeek-backend
mvn test
```

### 仓库守卫

```powershell
node scripts/check-no-automation.mjs
node scripts/check-no-cloud-server.mjs
```

## 开源范围说明

本仓库是 RankPeek 的开源版本，**不包含**以下功能的完整实现：

- **RP 指数**：基于对局数据的玩家评分系统
- **AI 分析**：赛前分析、赛后复盘、电子教练等 AI 功能

这些功能涉及核心算法和商业价值，为防止技术滥用（如魔改后售卖），相关代码不在开源仓库中。

开源版本保留了完整的：
- 桌面客户端框架（Electron + Vue 3）
- 本地后端服务架构（Spring Boot）
- LCU/SGP 数据读取
- 战绩查询和展示
- OP.GG 数据集成
- 本地缓存系统

## 数据与隐私

- 所有数据处理在本地完成，不依赖云端服务
- AI 凭据由用户自行配置，存储在本地
- 不收集用户个人信息
- 不要求注册账号或登录

## 已知限制

- 仅支持 Windows 系统
- LCU 相关功能需要英雄联盟客户端正在运行
- 部分数据依赖上游服务（Riot API、OP.GG），可能不可用
- OP.GG 数据可能有延迟

## 许可证

本项目基于 [MIT License](LICENSE) 发布。
