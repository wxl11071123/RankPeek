package io.rankpeek.jna;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows Kernel32 API 接口
 * 用于进程枚举和句柄管理
 */
public interface Kernel32 extends com.sun.jna.platform.win32.Kernel32 {

    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

    // 进程访问权限
    int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

    // CreateToolhelp32Snapshot 标志
    int TH32CS_SNAPPROCESS = 0x00000002;

    // ========== 进程枚举 API ==========
    WinNT.HANDLE CreateToolhelp32Snapshot(int dwFlags, int th32ProcessID);
    boolean Process32First(WinNT.HANDLE hSnapshot, Tlhelp32.PROCESSENTRY32 lppe);
    boolean Process32Next(WinNT.HANDLE hSnapshot, Tlhelp32.PROCESSENTRY32 lppe);

    // ========== 进程句柄 API ==========
    WinNT.HANDLE OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);
    boolean CloseHandle(WinNT.HANDLE hObject);
}
