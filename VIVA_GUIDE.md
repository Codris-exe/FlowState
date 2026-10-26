# 🎓 FlowState — Viva & Project Defense Guide (Minimal 6-File Edition)

This guide is designed for your **B.Tech CSE Project Viva / Presentation**. The entire application has been simplified into **only 6 clean Java files** in a single package (`com.flowstate`).

---

## 📌 1. Project Pitch (30-Second Elevator Pitch)

> *"FlowState is a native Java 21 desktop application that measures computer usage and detects digital distractions automatically in real time. It monitors the user's active window using native Windows Win32 API calls via JNA, classifies applications as Productive, Neutral, or Distracting, and calculates productivity trends without using any privacy-invasive keyloggers or screen capture. All activity logs are saved locally using Java File Input and Output Streams (`FileInputStream` / `FileOutputStream`)."*

---

## 📂 2. Complete Codebase Tour: All 6 Files Explained

Here is what each file does in 1 plain English sentence:

| # | File Name | What It Does |
|---|---|---|
| 1 | **`Main.java`** | Bootstrap launcher; invokes `FlowStateApp.main()` so the app runs smoothly with 1 click in any IDE (IntelliJ / Eclipse). |
| 2 | **`FlowStateApp.java`** | JavaFX stage initialization; sets up window dimensions, dark CSS theme, and registers the JVM shutdown hook. |
| 3 | **`MainController.java`** | Controls the UI; manages the 3 views (**Dashboard**, **Activity Timeline**, **Analytics**), charts, and pause/resume buttons. |
| 4 | **`ActivityTracker.java`** | Native Windows background tracker; uses **JNA** (`User32` & `Kernel32`) to sample the active window every 1.5s on a background thread. |
| 5 | **`ActivityStorage.java`** | File persistence engine; saves and loads activities to `./data/activities.json` using **`FileInputStream`** and **`FileOutputStream`**. |
| 6 | **`DistractionAnalyzer.java`** | Rule-based engine; classifies apps as `PRODUCTIVE` (e.g. IDEs, terminal), `DISTRACTING` (e.g. Discord, games), or `NEUTRAL` (e.g. browsers). |
| 7 | **`Activity.java`** | The data model representing a window interval (`applicationName`, `windowTitle`, `startedAt`, `durationSeconds`, and `Category` enum). |

---

## 🎯 3. Top 8 Viva Questions & Exact Answers

### Q1: What makes FlowState different from basic timer apps?
> **Answer**: *"Generic timer apps only run a clock and don't know what you are actually doing on your computer. FlowState automatically tracks which application you are using in the foreground, calculates your focus ratio, and detects when you switch to distracting apps in real time without needing manual timers."*

### Q2: How does the app save data without SQL or external databases?
> **Answer**: *"In `ActivityStorage.java`, we use low-level Java File Streams: `FileInputStream` and `BufferedInputStream` to read data, and `FileOutputStream` and `BufferedOutputStream` to write data into `./data/activities.json`. We use a `ReentrantReadWriteLock` so reading and writing remain thread-safe."*

### Q3: How do you detect the active window in Windows?
> **Answer**: *"In `ActivityTracker.java`, we use JNA (Java Native Access) to call native Win32 APIs in-memory:
> 1. `User32.INSTANCE.GetForegroundWindow()` gets the active window handle.
> 2. `User32.INSTANCE.GetWindowText()` gets the window title.
> 3. `Kernel32.INSTANCE.OpenProcess()` and `Psapi.INSTANCE.GetModuleFileNameExW()` get the executable process name.
> It takes less than 1 millisecond and consumes less than 0.2% CPU without spawning external CMD or PowerShell processes."*

### Q4: Does FlowState invade privacy like a keylogger?
> **Answer**: *"No. FlowState is strictly privacy-first:
> - Zero keystrokes captured (no keyboard hooks).
> - Zero screenshots or screen pixels read.
> - Zero clipboard access.
> - Only the foreground process name and window title are recorded."*

### Q5: How is multithreading implemented?
> **Answer**: *"We use:
> 1. A `ScheduledExecutorService` in `ActivityTracker.java` to sample the active window every 1.5 seconds on a background daemon thread.
> 2. `CompletableFuture.runAsync()` in `MainController.java` to compute metrics and read files without freezing the user interface.
> 3. `Platform.runLater()` whenever background threads need to update JavaFX UI labels or charts."*

### Q6: What OOP principles are demonstrated?
> **Answer**:
> - **Encapsulation**: Private fields in `Activity.java` with getters/setters and guarded domain logic (`close()`).
> - **Separation of Concerns**: UI (`MainController`), Business Logic (`DistractionAnalyzer`), Storage (`ActivityStorage`), OS Integration (`ActivityTracker`).
> - **Enum Types**: `Activity.Category` (`PRODUCTIVE`, `NEUTRAL`, `DISTRACTING`)."*

### Q7: How does distraction detection work? Does it use Machine Learning?
> **Answer**: *"No complex black-box ML is needed; it uses transparent, explainable rules in `DistractionAnalyzer.java`. Applications like IDEs, code editors, and terminals are classified as `PRODUCTIVE`, while games and social media are classified as `DISTRACTING`. A transition constitutes an interruption only when switching from productive work directly into a distracting tool."*

### Q8: How is application shutdown handled cleanly?
> **Answer**: *"In `FlowStateApp.java`, we register a JVM shutdown hook using `Runtime.getRuntime().addShutdownHook()`. When the user closes the window, the hook cleanly shuts down the background tracking thread and finalizes all active intervals to disk."*

### Q9: Why is there a separate `Main.java` launcher class instead of just running `FlowStateApp.java` directly?
> **Answer**: *"In modern Java (Java 11+), JavaFX was decoupled from the JDK. If you run a class directly extending `Application` in an IDE without module arguments, the JVM throws `Error: JavaFX runtime components are missing`. Having a lightweight launcher class `Main.java` that calls `FlowStateApp.main(args)` cleanly bypasses this check, allowing 1-click execution in IntelliJ IDEA or Eclipse."*

---

## 🚀 4. How to Run the App

1. **In IntelliJ IDEA / Eclipse**:
   - Right-click `Main.java` -> click **Run 'Main.main()'**
2. **From Terminal**:
   ```bash
   mvn javafx:run
   ```
3. **Run Automated Unit Tests**:
   ```bash
   mvn test
   ```
