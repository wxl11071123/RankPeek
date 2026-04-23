# GraalVM Native Image 打包指南

## 环境要求

### 1. 安装 GraalVM JDK 21

**下载地址**: https://www.oracle.com/java/technologies/downloads/#graalvm

选择 Windows x64 ZIP 或 MSI 安装包

**安装后设置环境变量**:
```powershell
# 方式 1: 在 build.bat 中设置（推荐）
set "GRAALVM_HOME=C:\path\to\graalvm-jdk-21"
set "JAVA_HOME=%GRAALVM_HOME%"

# 方式 2: 系统环境变量
GRAALVM_HOME = C:\path\to\graalvm-jdk-21
JAVA_HOME = %GRAALVM_HOME%
PATH = %GRAALVM_HOME%\bin;%PATH%
```

**验证安装**:
```bash
java -version
# 应该显示 GraalVM 相关信息
```

### 2. 安装 Visual Studio Build Tools

**下载地址**: https://visualstudio.microsoft.com/downloads/

**安装步骤**:
1. 下载 Visual Studio Installer
2. 选择 "Visual Studio Build Tools 2022"
3. 在工作负载中选择 **"使用 C++ 的桌面开发"**
4. 确保勾选以下组件:
   - MSVC v143 - VS 2022 C++ x64/x86 生成工具
   - Windows 10/11 SDK
   - C++ CMake 工具

### 3. 安装 Maven

```bash
# 使用 Chocolatey 安装
choco install maven

# 或手动下载安装
# https://maven.apache.org/download.cgi
```

## 打包步骤

### 方式 1: 使用 build.bat 脚本（推荐）

```bash
# 在项目根目录执行
build.bat
```

脚本会自动:
1. 检查环境（GraalVM、Maven、Node.js）
2. 初始化 MSVC 编译环境
3. 编译 Native Image
4. 构建前端 Electron 应用

**输出文件**: `rankpeek-backend/target/rankpeek-native.exe`

### 方式 2: 手动 Maven 编译

```bash
cd rankpeek-backend

# 初始化 MSVC 环境（重要！）
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

# 设置 GraalVM
set JAVA_HOME=C:\path\to\graalvm-jdk-21

# 编译 Native Image
mvn clean package -Pnative -DskipTests
```

## Native Image 配置说明

### pom.xml 关键配置

```xml
<!-- GraalVM Native Support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-native-image</artifactId>
    <scope>provided</scope>
</dependency>

<!-- Native Image 插件配置 -->
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
        <buildArgs>
            <!-- 不包含 JVM 回退模式 -->
            <buildArg>--no-fallback</buildArg>
            <!-- 启用 HTTP/HTTPS 协议 -->
            <buildArg>-H:EnableURLProtocols=http,https</buildArg>
            <!-- 启用所有字符集 -->
            <buildArg>-H:+AddAllCharsets</buildArg>
            <!-- 启用原生访问（JNA 需要） -->
            <buildArg>--enable-native-access=ALL-UNNAMED</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

### Native Image 配置文件

所有配置文件位于 `src/main/resources/META-INF/native-image/`:

1. **reflection-config.json** - 反射配置
   - 启用所有反射访问
   - 包含 application.yml 资源

2. **resource-config.json** - 资源包含配置
   - Spring Boot 自动配置索引
   - YAML 配置文件

3. **jna-config.json** - JNA 库配置
   - Native 方法反射
   - Windows API 访问

4. **okhttp-config.json** - OkHttp 配置
   - HTTP 客户端类反射

## 常见问题

### Q1: Native Image 编译失败 - "cl.exe not found"

**解决方案**:
```bash
# 确保已安装 Visual Studio Build Tools
# 手动初始化 MSVC 环境
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"

# 然后重新编译
mvn clean package -Pnative
```

### Q2: 运行时提示 "Could not find or load main class"

**解决方案**:
```bash
# 检查主类配置是否正确
# pom.xml 中应该配置:
<mainClass>io.rankpeek.RankPeekApplication</mainClass>

# 确认应用名称与实际的启动类一致
```

### Q3: LCU 连接失败或 JNA 相关错误

**解决方案**:
确保 jna-config.json 包含所有必要的 JNA 类反射配置

### Q4: OkHttp 或 WebSocket 相关错误

**解决方案**:
检查 okhttp-config.json 配置，确保所有 OkHttp 类都已注册反射

### Q5: Native Image 体积过大（>200MB）

**优化建议**:
```xml
<buildArgs>
    <!-- 优化体积 -->
    <buildArg>-O3</buildArg>
    <!-- 移除调试信息 -->
    <buildArg>-H:-DebugInfoSourceSearchPath</buildArg>
</buildArgs>
```

## 性能对比

| 指标 | JAR 包 | Native Image | 提升 |
|------|-------|--------------|------|
| **启动时间** | ~3 秒 | ~0.5 秒 | 6 倍 |
| **内存占用** | ~512MB | ~256MB | 50% |
| **文件大小** | ~80MB | ~200MB | - |
| **需要 JRE** | 是 | 否 | - |

## 运行 Native Image

```bash
# 直接运行（无需 JVM）
.\rankpeek-backend\target\rankpeek-native.exe

# 默认端口: 8080
# 访问：http://localhost:8080/api/v1/summoner/me
```

## 调试 Native Image

### 生成 Native Image 时输出详细日志

```bash
mvn clean package -Pnative -DskipTests \
  -Dnative.build.args="--verbose"
```

### 运行时调试

```bash
# 启用详细日志
.\rankpeek-native.exe --debug

# 查看 GraalVM 版本信息
.\rankpeek-native.exe --version
```

## 参考文档

- [Spring Boot GraalVM 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM Native Image 文档](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Native Image 配置指南](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildConfiguration.md)

## 技术支持

遇到问题请查看:
1. GraalVM 错误消息
2. Maven 构建日志
3. Spring Boot AOT 处理日志
