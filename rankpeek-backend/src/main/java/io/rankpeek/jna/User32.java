package io.rankpeek.jna;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface User32 extends StdCallLibrary {
    User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean EnumWindows(WinUser.WNDENUMPROC lpEnumFunc, Pointer data);

    boolean IsWindowVisible(WinDef.HWND hWnd);

    boolean IsIconic(WinDef.HWND hWnd);

    boolean GetWindowRect(WinDef.HWND hWnd, WinDef.RECT rect);

    int GetWindowThreadProcessId(WinDef.HWND hWnd, IntByReference processId);
}
