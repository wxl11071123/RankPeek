# 角色
你是一位精通 GraalVM Native Image 和 Windows 环境下 Java 原生化的专家。

# 背景
我正在使用GraalVM23  将一个 Spring Boot 3项目打包为 Windows .exe 文件。
构建脚本是：C:\Projects\RankPeek\rankpeek-backend\build-backend.bat

# 问题现象
生成的 .exe 文件在运行时出现以下问题：
1. 双击无反应
2. **错误日志**：
```text
java.lang.ClassNotFoundException: io.rankpeek.jna.Kernel32
```

# 任务
解决 GraalVM Native Image 中的反射和动态代理问题。

配置文件生成：
如果需要反射配置，请生成 reflection-config.json 片段。
如果需要资源配置，请生成 resource-config.json 片段。
如果需要 JNI 配置，请生成 jni-config.json 片段。
构建参数建议：告诉我需要在 native-image 命令中添加哪些 --initialize-at-build-time 或 --initialize-at-run-time 参数。
