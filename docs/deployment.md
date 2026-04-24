# RankPeek 部署指南

## 环境要求

### 开发环境

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ (GraalVM 推荐) | 用于后端开发和 Native Image 编译 |
| Node.js | 18+ | 用于前端开发 |
| Maven | 3.8+ | 后端构建工具 |
| Visual Studio Build Tools | 2022+ | Native Image 编译所需 (C++ 桌面开发工作负载) |

### 生产环境

- Windows 10/11 64位
- 无需安装 JRE（Native Image 自包含）

## 构建步骤

### 1. 克隆项目

```bash
git clone https://github.com/wxl11071123/RankPeek.git
cd RankPeek
```

### 2. 配置环境变量

复制环境变量模板：

```bash
cd rankpeek-backend
copy .env.example .env
```

编辑 `.env` 文件，配置运行环境：

```bash
SPRING_PROFILES_ACTIVE=prod
```

### 3. 一键构建

```bash
# 在项目根目录执行
build.bat
```

构建脚本会自动完成：
1. 检查环境（GraalVM、Maven、Node.js）
2. 初始化 MSVC 环境
3. 编译 Native Image
4. 构建前端 Electron 应用
5. 打包安装程序

### 4. 手动分步构建

#### 后端构建

```bash
cd rankpeek-backend

# 开发模式（JAR）
mvn spring-boot:run

# 生产模式（Native Image）
mvn clean package -Pnative -DskipTests
```

#### 前端构建

```bash
cd rankpeek-frontend

# 安装依赖
npm install

# 开发模式
npm run electron:dev

# 生产构建
npm run electron:build
```

## 安装步骤

### 方式一：安装包安装

1. 运行 `rankpeek-frontend/release/RankPeek Setup 1.0.0.exe`
2. 按照安装向导完成安装
3. 启动应用

### 方式二：免安装版

1. 复制 `rankpeek-frontend/release/win-unpacked/` 目录
2. 运行 `RankPeek.exe`

## 配置说明

### 后端配置

配置文件位于 `rankpeek-backend/src/main/resources/`：

| 文件 | 用途 |
|------|------|
| `application.yml` | 公共配置 |
| `application-dev.yml` | 开发环境配置 |
| `application-prod.yml` | 生产环境配置 |

### 前端配置

配置文件位于 `rankpeek-frontend/`：

| 文件 | 用途 |
|------|------|
| `package.json` | 项目配置和依赖 |
| `vite.config.ts` | Vite 构建配置 |
| `electron-builder.yml` | Electron 打包配置 |

## 常见问题

### 1. Native Image 编译失败

**问题**：`cl.exe` 未找到

**解决方案**：
- 安装 Visual Studio Build Tools
- 选择 "C++ 桌面开发" 工作负载
- 确保安装了 Windows SDK

### 2. 前端启动失败

**问题**：`Cannot find module`

**解决方案**：
```bash
cd rankpeek-frontend
rm -rf node_modules
npm install
```

### 3. LCU 连接失败

**问题**：应用无法连接到英雄联盟客户端

**解决方案**：
- 确保英雄联盟客户端已启动
- 检查客户端是否以管理员权限运行
- 重启应用

### 4. 端口被占用

**问题**：后端启动失败，端口 8080 被占用

**解决方案**：
- 修改 `application.yml` 中的 `server.port`
- 或关闭占用端口的程序

## 性能优化建议

### 内存优化

Native Image 默认内存限制较低，可通过以下方式调整：

```bash
# 设置最大堆内存
set JAVA_TOOL_OPTIONS=-Xmx512m
```

### 启动优化

Native Image 启动速度快，首次启动可能需要初始化缓存，后续启动会更快。

## 日志位置

| 环境 | 日志路径 |
|------|---------|
| 开发环境 | 控制台输出 |
| 生产环境 | `%APPDATA%/RankPeek/logs/` |

## 更新日志

查看 [README.md](../README.md) 中的版本历史。
