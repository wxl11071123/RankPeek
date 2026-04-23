package io.rankpeek.jna;

import io.rankpeek.model.AuthInfo;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows 进程工具类
 * 用于查找 LCU 进程并提取认证信息
 */
@Slf4j
public class ProcessUtils {

    private static final String LCU_PROCESS_NAME = "LeagueClientUx.exe";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("--remoting-auth-token=([\\w-]+)");
    private static final Pattern PORT_PATTERN = Pattern.compile("--app-port=(\\d+)");

    /**
     * 查找所有 LCU 进程 PID
     */
    public static List<Integer> findLcuProcesses() {
        List<Integer> pids = new ArrayList<>();

        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Kernel32.TH32CS_SNAPPROCESS, 0);

        if (snapshot == null || snapshot.equals(WinNT.INVALID_HANDLE_VALUE)) {
            log.error("创建进程快照失败");
            return pids;
        }

        try {
            Tlhelp32.PROCESSENTRY32 entry = new Tlhelp32.PROCESSENTRY32();

            if (Kernel32.INSTANCE.Process32First(snapshot, entry)) {
                do {
                    String processName = Native.toString(entry.szExeFile);
                    if (LCU_PROCESS_NAME.equalsIgnoreCase(processName)) {
                        pids.add(entry.th32ProcessID.intValue());
                        log.debug("找到 LCU 进程: PID={}", entry.th32ProcessID.intValue());
                    }
                } while (Kernel32.INSTANCE.Process32Next(snapshot, entry));
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }

        log.debug("找到 {} 个 LCU 进程", pids.size());
        return pids;
    }

    /**
     * 使用 JNA 方式读取进程命令行
     * 使用 ProcessCommandLineInformation (60)，只需要 PROCESS_QUERY_LIMITED_INFORMATION 权限
     */
    public static String getProcessCommandLine(int pid) {
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                Kernel32.PROCESS_QUERY_LIMITED_INFORMATION,
                false, pid);

        if (processHandle == null) {
            log.error("无法打开进程 {}", pid);
            return null;
        }

        try {
            IntByReference returnLength = new IntByReference();
            int initialSize = 8192;
            Memory buffer = new Memory(initialSize);

            int status = Ntdll.INSTANCE.NtQueryInformationProcess(
                    processHandle.getPointer(),
                    Ntdll.ProcessCommandLineInformation,
                    buffer,
                    initialSize,
                    returnLength
            );

            if (status != 0) {
                log.error("NtQueryInformationProcess 失败: 0x{}", Integer.toHexString(status));
                return null;
            }

            // 缓冲区开头是 UNICODE_STRING 结构体
            // typedef struct _UNICODE_STRING {
            //   USHORT Length;        // 偏移 0, 2 字节
            //   USHORT MaximumLength; // 偏移 2, 2 字节
            //   PWSTR  Buffer;        // 偏移 8 (对齐后), 8 字节 (x64)
            // } UNICODE_STRING;
            short length = buffer.getShort(0);
            Pointer bufferPtr = buffer.getPointer(8);

            if (length == 0 || bufferPtr == null) {
                log.error("命令行数据为空");
                return null;
            }

            // 获取字符串起始位置
            long bufferAddress = Pointer.nativeValue(bufferPtr);
            long baseAddress = Pointer.nativeValue(buffer);
            int offset = (int) (bufferAddress - baseAddress);

            if (offset < 0 || offset >= initialSize) {
                log.error("无效的缓冲区偏移: {}", offset);
                return null;
            }

            // 读取 UTF-16 字符串
            char[] chars = new char[length / 2];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = buffer.getChar(offset + i * 2);
            }

            return new String(chars);

        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle);
        }
    }

    /**
     * 从命令行参数中提取认证信息
     */
    public static AuthInfo extractAuthInfo(String commandLine) {
        if (commandLine == null || commandLine.isEmpty()) {
            return null;
        }

        String token = null;
        String port = null;

        Matcher tokenMatcher = TOKEN_PATTERN.matcher(commandLine);
        if (tokenMatcher.find()) {
            token = tokenMatcher.group(1);
        }

        Matcher portMatcher = PORT_PATTERN.matcher(commandLine);
        if (portMatcher.find()) {
            port = portMatcher.group(1);
        }

        if (token != null && port != null) {
            AuthInfo authInfo = new AuthInfo();
            authInfo.setToken(token);
            authInfo.setPort(port);
            log.debug("成功提取 LCU 认证信息: port={}", port);
            return authInfo;
        }

        log.warn("无法从命令行提取完整认证信息");
        return null;
    }

    /**
     * 获取 LCU 认证信息（主入口）
     */
    public static AuthInfo getLcuAuthInfo() {
        // 从进程命令行读取
        List<Integer> pids = findLcuProcesses();

        if (pids.isEmpty()) {
            log.warn("未找到 LCU 进程，请确保游戏客户端已启动");
            return null;
        }

        for (Integer pid : pids) {
            log.debug("尝试读取进程 PID={}", pid);
            String commandLine = getProcessCommandLine(pid);
            AuthInfo authInfo = extractAuthInfo(commandLine);
            if (authInfo != null) {
                authInfo.setPid(pid);
                return authInfo;
            }
        }

        log.error("无法从任何 LCU 进程获取认证信息");
        return null;
    }
}
