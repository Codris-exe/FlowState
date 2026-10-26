package com.flowstate.tracking;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.Optional;

/**
 * Native Windows implementation of ActivityTrackingProvider using JNA Win32 APIs.
 *
 * Polling foreground window requires sub-millisecond execution and produces zero external
 * process spawning.
 *
 * Privacy Notice (TRAK-06):
 * This class reads ONLY the foreground window title and process image filename.
 * It contains ZERO keyboard hooks, ZERO screen capture/pixel reading, and ZERO clipboard access.
 */
public class WindowsNativeTrackingProvider implements ActivityTrackingProvider {
    private static final Logger logger = LoggerFactory.getLogger(WindowsNativeTrackingProvider.class);

    private final boolean isWindows;

    public WindowsNativeTrackingProvider() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        this.isWindows = osName.contains("win");
        if (isWindows) {
            logger.info("WindowsNativeTrackingProvider initialized on Windows OS");
        } else {
            logger.warn("WindowsNativeTrackingProvider running on non-Windows OS ('{}'); native tracking disabled", osName);
        }
    }

    @Override
    public boolean isSupported() {
        return isWindows;
    }

    @Override
    public Optional<ForegroundAppInfo> getForegroundApp() {
        if (!isWindows) {
            return Optional.empty();
        }

        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) {
                return Optional.empty();
            }

            // 1. Query Window Title
            char[] titleBuffer = new char[512];
            int titleLen = User32.INSTANCE.GetWindowText(hwnd, titleBuffer, 512);
            String windowTitle = titleLen > 0 ? new String(titleBuffer, 0, titleLen).trim() : "";

            // 2. Query Process ID
            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            int pid = pidRef.getValue();
            if (pid <= 0) {
                return Optional.of(new ForegroundAppInfo("System", windowTitle, Instant.now()));
            }

            // 3. Query Process Image Executable Name
            String processName = getProcessExecutableName(pid);

            return Optional.of(new ForegroundAppInfo(processName, windowTitle, Instant.now()));
        } catch (Exception e) {
            logger.debug("Error sampling native foreground window: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getProcessExecutableName(int pid) {
        // Try with PROCESS_QUERY_LIMITED_INFORMATION (available on Vista+)
        int desiredAccess = WinNT.PROCESS_QUERY_LIMITED_INFORMATION;
        HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(desiredAccess, false, pid);

        if (processHandle == null) {
            // Fallback for older configurations
            desiredAccess = WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ;
            processHandle = Kernel32.INSTANCE.OpenProcess(desiredAccess, false, pid);
        }

        if (processHandle == null) {
            return "System";
        }

        try {
            char[] exePath = new char[1024];
            IntByReference size = new IntByReference(exePath.length);

            if (Kernel32.INSTANCE.QueryFullProcessImageName(processHandle, 0, exePath, size)) {
                String fullPath = new String(exePath, 0, size.getValue());
                return extractProcessName(fullPath);
            }

            // Secondary fallback via Psapi
            int psapiLen = Psapi.INSTANCE.GetModuleFileNameExW(processHandle, null, exePath, 1024);
            if (psapiLen > 0) {
                String fullPath = new String(exePath, 0, psapiLen);
                return extractProcessName(fullPath);
            }
        } catch (Exception e) {
            logger.debug("Failed to query executable name for pid {}: {}", pid, e.getMessage());
        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle);
        }

        return "Unknown";
    }

    private String extractProcessName(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) {
            return "Unknown";
        }
        int lastBackslash = fullPath.lastIndexOf('\\');
        int lastSlash = fullPath.lastIndexOf('/');
        int separator = Math.max(lastBackslash, lastSlash);
        if (separator >= 0 && separator < fullPath.length() - 1) {
            return fullPath.substring(separator + 1).toLowerCase();
        }
        return fullPath.toLowerCase();
    }
}
