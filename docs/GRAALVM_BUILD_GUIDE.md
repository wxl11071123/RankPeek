# GraalVM Native Image 构建 Windows EXE 完全指南

## 目录
1. [环境准备](#1-环境准备)
2. [项目配置](#2-项目配置)
3. [配置文件详解](#3-配置文件详解)
4. [构建步骤](#4-构建步骤)
5. [常见问题与解决方案](#5-常见问题与解决方案)
6. [性能优化建议](#6-性能优化建议)

---

## 1. 环境准备

### 1.1 必需组件

| 组件 | 版本要求 | 下载地址 |
|------|---------|---------|
| GraalVM JDK | 21+ | https://www.graalvm.org/downloads/ |
| Maven | 3.8+ | https://maven.apache.org/download.cgi |
| Visual Studio Build Tools | 2019+ | https://visualstudio.microsoft.com/downloads/ |
| Windows SDK | 10+ | 随 VS Build Tools 安装 |

### 1.2 安装 Visual Studio Build Tools

安装时必须勾选 **"使用 C++ 的桌面开发"** 工作负载：

```bash
# 或使用 vswhere 检查是否已安装
"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
```

### 1.3 环境变量配置

```bash
# Windows 系统环境变量
GRAALVM_HOME=C:\develop\graalvm-jdk-21.0.10+8.1
JAVA_HOME=%GRAALVM_HOME%
PATH=%GRAALVM_HOME%\bin;%PATH%
```

验证安装：
```bash
java -version
# 应显示 GraalVM 版本信息

native-image --version
# 应显示 native-image 版本
```

---

## 2. 项目配置

### 2.1 pom.xml 配置

```xml
<properties>
    <java.version>21</java.version>
    <jna.version>5.14.0</jna.version>
    <graalvm.version>0.10.6</graalvm.version>
</properties>

<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <!-- Spring Boot AOT 处理 -->
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>process-aot</id>
                            <goals>
                                <goal>process-aot</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>

                <!-- GraalVM Native Image -->
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>${graalvm.version}</version>
                    <extensions>true</extensions>
                    <executions>
                        <execution>
                            <id>build-native</id>
                            <goals>
                                <goal>compile-no-fork</goal>
                            </goals>
                            <phase>package</phase>
                        </execution>
                    </executions>
                    <configuration>
                        <mainClass>io.rankpeek.RankPeekApplication</mainClass>
                        <imageName>rankpeek-native</imageName>
                        <buildArgs>
                            <!-- 基础配置 -->
                            <buildArg>--no-fallback</buildArg>
                            <buildArg>-H:EnableURLProtocols=http,https</buildArg>
                            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                            <buildArg>-H:+AddAllCharsets</buildArg>

                            <!-- 资源包含 -->
                            <buildArg>-H:IncludeResources=application\.yml</buildArg>

                            <!-- 初始化配置 - 构建时 -->
                            <buildArg>--initialize-at-build-time=org.slf4j.LoggerFactory</buildArg>

                            <!-- 初始化配置 - 运行时 (JNA 必须运行时初始化) -->
                            <buildArg>--initialize-at-run-time=okhttp3.internal.platform.Platform</buildArg>
                            <buildArg>--initialize-at-run-time=io.netty.util.internal.logging.Log4JLogger</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.Native</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.Native$DeleteNativeLibrary</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.Pointer</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.Memory</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.CallbackReference</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.Structure</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.platform.win32.Kernel32</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.platform.win32.WinNT</buildArg>
                            <buildArg>--initialize-at-run-time=com.sun.jna.platform.win32.Tlhelp32</buildArg>

                            <!-- 配置文件路径 -->
                            <buildArg>-H:ReflectionConfigurationFiles=${project.basedir}/src/main/resources/META-INF/native-image/reflect-config.json</buildArg>
                            <buildArg>-H:DynamicProxyConfigurationFiles=${project.basedir}/src/main/resources/META-INF/native-image/proxy-config.json</buildArg>
                            <buildArg>-H:ResourceConfigurationFiles=${project.basedir}/src/main/resources/META-INF/native-image/resource-config.json</buildArg>
                        </buildArgs>
                        <metadataRepository>
                            <enabled>true</enabled>
                        </metadataRepository>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

---

## 3. 配置文件详解

### 3.1 目录结构

```
src/main/resources/META-INF/native-image/
├── reflect-config.json      # 反射配置（最重要）
├── proxy-config.json        # 动态代理配置
├── resource-config.json     # 资源文件配置
└── jni-config.json          # JNI 配置（可选）
```

### 3.2 reflect-config.json

**格式要求**：必须是 JSON 数组格式（GraalVM 21+）

```json
[
  {
    "name": "com.example.MyClass",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true,
    "allDeclaredFields": true,
    "allPublicFields": true,
    "allDeclaredClasses": true,
    "allPublicClasses": true
  }
]
```

**关键类配置清单**：

#### Spring Boot 类
- `com.example.Application` - 主类
- 所有 `@Component`, `@Service`, `@Controller` 类
- 所有 `@Configuration` 类
- 所有 `@Entity` 类（如果使用 JPA）

#### Jackson 反序列化类
- 所有 JSON 映射的 POJO 类
- 所有内部静态类（如 `AssetService$Item`）

#### JNA Windows API 类（完整列表）
```json
[
  // JNA 核心
  "com.sun.jna.Native",
  "com.sun.jna.Pointer",
  "com.sun.jna.Structure",
  "com.sun.jna.Structure$ByValue",
  "com.sun.jna.Memory",
  "com.sun.jna.Callback",
  "com.sun.jna.CallbackReference",
  "com.sun.jna.IntegerType",
  "com.sun.jna.NativeLong",
  "com.sun.jna.PointerType",
  "com.sun.jna.WString",

  // JNA 指针类型
  "com.sun.jna.ptr.PointerByReference",
  "com.sun.jna.ptr.IntByReference",
  "com.sun.jna.ptr.LongByReference",
  "com.sun.jna.ptr.ByteByReference",
  "com.sun.jna.ptr.ShortByReference",

  // Win32 API
  "com.sun.jna.win32.W32APIOptions",
  "com.sun.jna.win32.W32APITypeMapper",
  "com.sun.jna.win32.StdCallLibrary",
  "com.sun.jna.win32.W32APIFunctionMapper",
  "net.java.dev.jna.Platform",

  // Windows 平台
  "com.sun.jna.platform.win32.Kernel32",
  "com.sun.jna.platform.win32.Tlhelp32",
  "com.sun.jna.platform.win32.Tlhelp32$PROCESSENTRY32",
  "com.sun.jna.platform.win32.Tlhelp32$PROCESSENTRY32W",
  "com.sun.jna.platform.win32.Tlhelp32$THREADENTRY32",
  "com.sun.jna.platform.win32.Tlhelp32$MODULEENTRY32",
  "com.sun.jna.platform.win32.Tlhelp32$MODULEENTRY32W",
  "com.sun.jna.platform.win32.WinNT",
  "com.sun.jna.platform.win32.WinNT$HANDLE",
  "com.sun.jna.platform.win32.WinNT$HANDLEByReference",
  "com.sun.jna.platform.win32.WinBase",
  "com.sun.jna.platform.win32.WinBase$FILETIME",
  "com.sun.jna.platform.win32.WinBase$SYSTEMTIME",
  "com.sun.jna.platform.win32.WinBase$SECURITY_ATTRIBUTES",
  "com.sun.jna.platform.win32.WinDef",
  "com.sun.jna.platform.win32.WinDef$DWORD",
  "com.sun.jna.platform.win32.WinDef$DWORDByReference",
  "com.sun.jna.platform.win32.WinDef$ULONG",
  "com.sun.jna.platform.win32.WinDef$ULONGByReference",
  "com.sun.jna.platform.win32.WinDef$LONG",
  "com.sun.jna.platform.win32.WinDef$WORD",
  "com.sun.jna.platform.win32.WinDef$WORDByReference",
  "com.sun.jna.platform.win32.WinDef$BYTE",
  "com.sun.jna.platform.win32.WinDef$BOOL",
  "com.sun.jna.platform.win32.WinDef$BOOLByReference",
  "com.sun.jna.platform.win32.WinDef$HMODULE",
  "com.sun.jna.platform.win32.WinDef$HWND",
  "com.sun.jna.platform.win32.WinDef$LPARAM",
  "com.sun.jna.platform.win32.WinDef$WPARAM",
  "com.sun.jna.platform.win32.WinDef$LRESULT",
  "com.sun.jna.platform.win32.WinDef$RECT",
  "com.sun.jna.platform.win32.WinDef$POINT",
  "com.sun.jna.platform.win32.WinDef$SIZE",
  "com.sun.jna.platform.win32.WinDef$ATOM",
  "com.sun.jna.platform.win32.WinDef$HCURSOR",
  "com.sun.jna.platform.win32.WinDef$HICON",
  "com.sun.jna.platform.win32.WinDef$HINSTANCE",
  "com.sun.jna.platform.win32.WinDef$HMENU",
  "com.sun.jna.platform.win32.WinDef$HRGN",
  "com.sun.jna.platform.win32.BaseTSD",
  "com.sun.jna.platform.win32.BaseTSD$ULONG_PTR",
  "com.sun.jna.platform.win32.BaseTSD$LONG_PTR",
  "com.sun.jna.platform.win32.BaseTSD$DWORD_PTR",
  "com.sun.jna.platform.win32.BaseTSD$UINT_PTR",
  "com.sun.jna.platform.win32.BaseTSD$SIZE_T",
  "com.sun.jna.platform.win32.BaseTSD$SSIZE_T",
  "com.sun.jna.platform.win32.Psapi",
  "com.sun.jna.platform.win32.Psapi$PROCESS_MEMORY_COUNTERS",
  "com.sun.jna.platform.win32.Psapi$PERFORMANCE_INFORMATION"
]
```

### 3.3 proxy-config.json

```json
[
  {
    "interfaces": ["com.example.jna.Kernel32"]
  },
  {
    "interfaces": ["com.example.jna.Ntdll"]
  }
]
```

**注意**：只包含自定义接口，不包含 JNA 平台接口。

### 3.4 resource-config.json

```json
{
  "bundles": [],
  "resources": [
    {"pattern": "\\Qapplication.yml\\E"},
    {"pattern": "\\Qapplication.yaml\\E"},
    {"pattern": "\\Qapplication-prod.yml\\E"},
    {"pattern": "\\Qapplication-dev.yml\\E"},
    {"pattern": "META-INF/services/.*"},
    {"pattern": "\\QMETA-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports\\E"}
  ]
}
```

---

## 4. 构建步骤

### 4.1 完整构建脚本 (build.bat)

```batch
@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

echo ========================================
echo   Native Image 打包脚本
echo ========================================
echo.

:: 记录开始时间
set "START_TIME=%time%"

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%"

:: 设置 GraalVM 路径
set "GRAALVM_HOME=C:\develop\graalvm-jdk-21.0.10+8.1"
set "JAVA_HOME=%GRAALVM_HOME%"
set "PATH=%GRAALVM_HOME%\bin;%PATH%"

echo [1/4] 检查环境...

if not exist "%GRAALVM_HOME%\bin\java.exe" (
    echo 错误: 未找到 GraalVM
    pause
    exit /b 1
)

where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到 Maven
    pause
    exit /b 1
)

echo    GraalVM: %GRAALVM_HOME%
echo    Maven: OK
echo ✓ 环境检查通过
echo.

echo [2/4] 初始化 MSVC 环境...

set "VCVARS_FOUND=0"

:: 使用 vswhere 自动查找 VS 安装路径
if exist "%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" (
    for /f "usebackq tokens=*" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        if exist "%%i\VC\Auxiliary\Build\vcvars64.bat" (
            call "%%i\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
            set "VCVARS_FOUND=1"
            echo    使用 VS: %%i
        )
    )
)

if "%VCVARS_FOUND%"=="0" (
    echo 警告: 未找到 MSVC 环境，尝试继续...
) else (
    echo ✓ MSVC 环境已初始化
)
echo.

echo [3/4] 编译 Native Image...
cd /d "%BACKEND_DIR%"

call mvn clean package -Pnative -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo 错误: Native Image 编译失败
    echo.
    pause
    exit /b 1
)

if not exist "target\rankpeek-native.exe" (
    echo 错误: 未找到 rankpeek-native.exe
    pause
    exit /b 1
)

:: 正确计算文件大小 (MB)
for %%A in ("target\rankpeek-native.exe") do set "NATIVE_SIZE_BYTES=%%~zA"
set /a "NATIVE_SIZE_MB=%NATIVE_SIZE_BYTES%/1024/1024"
echo ✓ Native Image 编译完成 (大小: %NATIVE_SIZE_MB% MB)
echo.

echo [4/4] 构建完成
echo 输出文件: target\rankpeek-native.exe
echo.

:: 计算耗时
set "END_TIME=%time%"
echo 开始时间: %START_TIME%
echo 结束时间: %END_TIME%

echo.
echo 运行测试:
echo   .\target\rankpeek-native.exe
echo.

pause
```

### 4.2 构建命令

```bash
# 完整构建
cd rankpeek-backend
mvn clean package -Pnative -DskipTests

# 如果只想编译 native-image（跳过测试和 AOT）
mvn native:compile -Pnative

# 调试构建（显示详细日志）
mvn clean package -Pnative -DskipTests -X
```

---

## 5. 常见问题与解决方案

### 5.1 ClassNotFoundException

**错误**：`java.lang.ClassNotFoundException: com.example.Xxx`

**解决**：将该类添加到 `reflect-config.json`

### 5.2 Jackson 反序列化失败

**错误**：`Cannot construct instance of Xxx: cannot deserialize from Object value`

**解决**：
1. 确保 POJO 有无参构造函数
2. 将类添加到 `reflect-config.json`
3. 或使用 `@RegisterForReflection` 注解

### 5.3 JNA 初始化错误

**错误**：`Invalid Structure field` 或 `Can't create an instance of class`

**解决**：
1. 确保 `--initialize-at-run-time` 包含所有 JNA 类
2. 将 JNA 结构体类添加到 `reflect-config.json`

### 5.4 资源文件缺失

**错误**：`FileNotFoundException: application.yml`

**解决**：
1. 将资源模式添加到 `resource-config.json`
2. 使用 `-H:IncludeResources=...` 参数

### 5.5 MSVC 环境未找到

**错误**：`cl.exe not found` 或编译失败

**解决**：
```batch
# 手动初始化 MSVC 环境
"C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
```

### 5.6 构建过程卡住

**原因**：Native Image 编译需要大量内存

**解决**：
```bash
# 增加 Maven 内存
set MAVEN_OPTS=-Xmx8g

# 或在 pom.xml 中配置
<buildArgs>
    <buildArg>-J-Xmx8g</buildArg>
</buildArgs>
```

---

## 6. 性能优化建议

### 6.1 减少镜像大小

```xml
<!-- 使用 UPX 压缩（可选） -->
<buildArg>--compress=2</buildArg>

<!-- 移除调试信息 -->
<buildArg>-H:-Debug</buildArg>

<!-- 禁用某些特性 -->
<buildArg>--no-fallback</buildArg>
```

### 6.2 提高启动速度

```xml
<!-- 使用 G1GC（默认） -->
<buildArg>--gc=G1</buildArg>

<!-- 或 SerialGC（内存占用更小） -->
<buildArg>--gc=Serial</buildArg>

<!-- 启用快速启动 -->
<buildArg>-H:+FastStart</buildArg>
```

### 6.3 内存优化

```xml
<!-- 预初始化更多类（减少运行时初始化时间） -->
<buildArg>--initialize-at-build-time=com.example</buildArg>

<!-- 限制线程栈大小 -->
<buildArg>-H:StackSize=1m</buildArg>
```

### 6.4 运行时参数

```bash
# 运行时可调整参数
rankpeek-native.exe -Xmx512m -Xms128m

# 启用 JIT 编译器优化（默认已启用）
rankpeek-native.exe -XX:+UseParallelGC
```

---

## 附录：调试技巧

### 生成调用树分析

```xml
<buildArg>-H:+PrintAnalysisCallTree</buildArg>
<buildArg>-H:+PrintImageElementSizes</buildArg>
```

### 启用详细日志

```xml
<buildArg>--verbose</buildArg>
```

### 追踪类初始化

```xml
<buildArg>--trace-class-initialization=com.example</buildArg>
```

### 生成报告

```xml
<buildArg>-H:+ReportExceptionStackTraces</buildArg>
```

---

## 参考文档

- [GraalVM Native Image 官方文档](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring Boot GraalVM 指南](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [JNA Platform API](https://java-native-access.github.io/jna/5.14.0/javadoc/)
