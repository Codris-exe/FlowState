package com.focusforge;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ActivityTracker {
    private static final ActivityTracker INSTANCE = new ActivityTracker();

    private final boolean isWindows;
    private final ActivityStorage storage;
    private final DistractionAnalyzer analyzer;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean trackingEnabled = new AtomicBoolean(true);
    private final AtomicReference<Activity> currentActivity = new AtomicReference<>(null);
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private ScheduledFuture<?> pollingFuture;

    public ActivityTracker() {
        this(ActivityStorage.getInstance(), DistractionAnalyzer.getInstance(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "FocusForge-Tracker");
                    t.setDaemon(true);
                    return t;
                }));
    }

    public ActivityTracker(ActivityStorage storage, DistractionAnalyzer analyzer, ScheduledExecutorService scheduler) {
        this.storage = storage;
        this.analyzer = analyzer;
        this.scheduler = scheduler;
        this.isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static ActivityTracker getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (pollingFuture != null && !pollingFuture.isCancelled()) return;
        if (!isWindows) {
            System.err.println("[ActivityTracker] Native tracking only supported on Windows OS.");
            return;
        }

        System.out.println("[ActivityTracker] Starting background tracking (1.5s interval)...");
        pollingFuture = scheduler.scheduleWithFixedDelay(this::pollForegroundWindow, 0, 1500, TimeUnit.MILLISECONDS);
    }

    public synchronized void pauseTracking() {
        if (trackingEnabled.compareAndSet(true, false)) {
            System.out.println("[ActivityTracker] Tracking paused by user");
            finalizeCurrentActivity(Instant.now());
            notifyListeners();
        }
    }

    public synchronized void resumeTracking() {
        if (trackingEnabled.compareAndSet(false, true)) {
            System.out.println("[ActivityTracker] Tracking resumed by user");
            notifyListeners();
        }
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled.get();
    }

    public Activity getCurrentActivity() {
        return currentActivity.get();
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null) changeListeners.add(listener);
    }

    public void pollForegroundWindow() {
        if (!trackingEnabled.get() || !isWindows) return;

        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) return;

            char[] titleBuffer = new char[512];
            int titleLen = User32.INSTANCE.GetWindowText(hwnd, titleBuffer, 512);
            String title = titleLen > 0 ? new String(titleBuffer, 0, titleLen).trim() : "";

            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            int pid = pidRef.getValue();
            if (pid <= 0) return;

            String processName = getProcessName(pid);
            Instant now = Instant.now();

            Activity current = currentActivity.get();
            if (current == null) {
                Activity.Category cat = analyzer.resolveCategory(processName);
                Activity newAct = new Activity(processName, title, now, cat);
                currentActivity.set(newAct);
                notifyListeners();
                return;
            }

            boolean sameApp = processName.equalsIgnoreCase(current.getApplicationName());
            boolean sameTitle = title.equals(current.getWindowTitle());

            if (sameApp && sameTitle) {
                current.close(now);
            } else {
                finalizeCurrentActivity(now);
                Activity.Category cat = analyzer.resolveCategory(processName);
                Activity newAct = new Activity(processName, title, now, cat);
                currentActivity.set(newAct);
                notifyListeners();
            }
        } catch (Exception ignored) {
        }
    }

    private void finalizeCurrentActivity(Instant now) {
        Activity prev = currentActivity.getAndSet(null);
        if (prev != null) {
            prev.close(now);
            if (storage != null) {
                storage.save(prev);
                System.out.println("[ActivityTracker] Saved activity: " + prev.getApplicationName() + " (" + prev.getDurationSeconds() + "s)");
            }
        }
    }

    private String getProcessName(int pid) {
        int desiredAccess = WinNT.PROCESS_QUERY_LIMITED_INFORMATION;
        HANDLE handle = Kernel32.INSTANCE.OpenProcess(desiredAccess, false, pid);
        if (handle == null) {
            desiredAccess = WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ;
            handle = Kernel32.INSTANCE.OpenProcess(desiredAccess, false, pid);
        }
        if (handle == null) return "System";

        try {
            char[] path = new char[1024];
            int len = Psapi.INSTANCE.GetModuleFileNameExW(handle, null, path, 1024);
            if (len > 0) {
                return new File(new String(path, 0, len)).getName();
            }
        } catch (Exception ignored) {
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
        return "System";
    }

    private void notifyListeners() {
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }

    public synchronized void shutdown() {
        if (pollingFuture != null) {
            pollingFuture.cancel(false);
            pollingFuture = null;
        }
        finalizeCurrentActivity(Instant.now());
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
