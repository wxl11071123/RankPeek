package io.rankpeek.jna;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows NTDLL API 接口
 * 用于读取进程命令行参数
 */
public interface Ntdll extends com.sun.jna.Library {

    Ntdll INSTANCE = Native.load("ntdll", Ntdll.class, W32APIOptions.DEFAULT_OPTIONS);

    // PROCESSINFOCLASS 枚举值
    int ProcessCommandLineInformation = 60;

    /**
     * 查询进程信息
     * 用于 ProcessCommandLineInformation (60)
     */
    int NtQueryInformationProcess(Pointer ProcessHandle, int ProcessInformationClass,
                                   Pointer ProcessInformation, int ProcessInformationLength,
                                   IntByReference ReturnLength);
}
