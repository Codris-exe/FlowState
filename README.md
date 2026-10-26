# FlowState — Smart Distraction & Productivity Analyzer

A native Java 21 desktop application designed to help developers, students, and knowledge workers understand their computer usage, track deep-work focus sessions, eliminate digital distractions, and analyze productivity trends.

Developed as a college-level **B.Tech Computer Science & Engineering (CSE)** capstone project, FlowState demonstrates advanced Java Object-Oriented Programming (OOP), multithreading, concurrency synchronization, high-performance Java I/O Streams (`FileInputStream`, `FileOutputStream`, `BufferedInputStream`, `BufferedOutputStream`), native Windows integration via JNA, modern JavaFX UI/UX design, and optional PostgreSQL JDBC persistence.

---

## 💾 Storage Architecture: Zero-Setup File Storage

FlowState supports **two persistence backends** via clean repository interfaces (`RepositoryFactory`):

1. **File Storage with Java I/O Streams (Default & Zero Setup)**:
   - Persists all focus sessions, application activities, and preferences to local structured files in `./data/`.
   - Uses low-level **`FileInputStream`**, **`BufferedInputStream`**, **`FileOutputStream`**, and **`BufferedOutputStream`** to demonstrate foundational Java I/O stream mechanics.
   - Requires **zero external database setup**, no server installation, and runs out-of-the-box on any computer.
2. **PostgreSQL Database (Optional Enterprise Mode)**:
   - For environments where a relational database server is desired, FlowState includes complete JDBC DAO implementations with HikariCP connection pooling and automatic schema initialization.

---

## 🌟 Key Features

### 1. Dashboard Overview
- **Live Greeting & Date**: Context-aware greeting and formatted localized date.
- **Daily Focus Goal Progress**: Visual progress bar tracking actual deep work against your daily target (e.g. 120 minutes).
- **Core Stat Cards**: Instant feedback on today's focus time, completed sessions, interruption counts, and average session duration.
- **7-Day Focus Chart**: Interactive JavaFX bar chart visualizing focus minutes for each day of the previous week.
- **Top Applications Breakdown**: Ranking of today's most-used applications with formatted durations and categories.
- **Recent Sessions & Transparent Insights**: Review recent focus blocks and read explainable productivity summaries generated directly from real activity logs.

### 2. Focus Session Timer
- **Single-Active-Session Concurrency**: Enforces one focus block at a time to keep you locked on your goal.
- **Custom Goals & Categories**: Categorize sessions under `Coding`, `Studying`, `Reading`, `Writing`, `Research`, or `Other`.
- **Flexible Target Durations**: Quick preset selectors (15m, 25m Pomodoro, 45m, 60m, 90m Ultra) or custom goals.
- **Drift-Free Wall-Clock Timing**: Uses `java.time.Instant` intervals rather than naive loop counters, ensuring sub-second accuracy even if the application is minimized or the OS sleeps.
- **Full Lifecycle Control**: Seamlessly start, pause, resume, finish early, or cancel sessions.
- **Post-Session Reflections**: Prompt dialog to record session notes and reflections, durably stored in PostgreSQL.

### 3. Live Activity Monitor
- **Sub-Millisecond Native Tracking**: In-process JNA Win32 queries (`User32` & `Kernel32`) sample the active foreground window every 1.5s with <0.2% CPU overhead and zero external process spawning.
- **Live Spotlight Card**: Real-time display of current executable name, window title, category pill, and continuous interval duration.
- **In-Place Classification**: One-click buttons to classify applications as `Productive`, `Neutral`, or `Distracting`.
- **Live Activity Stream**: Real-time filterable table showing recently logged application switches.

### 4. Transparent Distraction Analysis
- **Rule-Based Engine (No Black-Box ML)**: Explainable heuristics distinguish genuine distractions from productive multitasking.
- **Legitimate Work Transitions**: Switching from an IDE to browser documentation or terminal is recognized as `Neutral` work and is **never** penalized.
- **Interruption Detection**: Only transitions from `Productive` to `Distracting` applications increment session interruption counts.
- **Deep-Work Streak Metric**: Calculates the longest uninterrupted focus interval for every session.
- **Focus Percentage**: Mathematically measures the exact proportion of session time spent in productive tools.

### 5. Productivity Analytics
- **Multi-Range Timeframes**: Filter analytical reports by `Today`, `Last 7 Days`, `Last 30 Days`, or `All Time`.
- **Duration Trends**: JavaFX bar charts displaying daily focus volume over the selected timeframe.
- **Application Distribution**: JavaFX pie charts illustrating the percentage split between productive, neutral, and distracting tools.
- **Interruption Patterns**: Bar charts graphing total interruptions grouped by session category.

### 6. Searchable Session History
- **Search & Filter**: Real-time text search across session titles and reflections, with multi-criteria category and status filters.
- **In-Place Notes Editor**: Inspect and edit session reflections with immediate database persistence.
- **Cascading Deletion**: Delete past sessions with two-step confirmation, cleaning up linked activity intervals automatically.

### 7. Reports & Data Export (Java NIO)
- **RFC 4180 Compliant CSV Export**: High-performance, non-blocking file streaming using Java NIO (`Files.newBufferedWriter`).
- **Data Portability**: Export both Focus Sessions and Application Activity logs with proper quote, comma, and newline escaping.
- **Overwrite Protection**: Safety checks warn before overwriting existing files.

### 8. Settings & Privacy Controls
- **Daily Focus Target**: Adjust your daily focus goal in minutes.
- **Privacy Exclusion Rules**: Flag sensitive apps (e.g. password managers, banking apps) as excluded — they are completely dropped from tracking and never saved.
- **Rules Import/Export**: Backup or share application categorization rules via CSV.
- **Data Management (Danger Zone)**: Two-step confirmation dialogs to clear activity logs or perform a full database reset.

---

## 🛠️ Technology Stack

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Language** | Java | 21 (LTS) | Core application business logic, concurrency, OOP |
| **GUI Framework** | JavaFX | 21.0.5 | Desktop user interface, controls, charts, animations |
| **Layouts & Styling**| FXML & CSS | — | Declarative UI structure and dark navy/charcoal styling |
| **Storage** | File Streams (Jackson JSON) | 2.17.1 | Local file I/O with FileInputStream/FileOutputStream |
| **Native OS Access** | JNA & JNA Platform | 5.14.0 | Direct Win32 User32/Kernel32 native API calls |
| **Logging** | Standard Java I/O | — | System.out & System.err console diagnostics |
| **Testing** | JUnit 5 & AssertJ | 5.11.3 / 3.26.3 | Automated unit test suite |
| **Build Tool** | Apache Maven | 3.9+ | Dependency management and compilation |

---

## 🔒 Privacy Architecture

FlowState was engineered with a strict **Privacy-First Mandate** (`TRAK-06`):
- 🚫 **Zero Keylogging**: No keyboard hooks (`WH_KEYBOARD_LL`). Zero keystrokes captured.
- 🚫 **Zero Screenshots**: No screen pixel reading or desktop capture.
- 🚫 **Zero Clipboard Monitoring**: FlowState never inspects or interacts with the system clipboard.
- 🚫 **Zero Web Surfing Inspection**: Only the active window title is recorded; full browser history or document contents are never accessed.
- 🚫 **Exclusion Filtering**: Excluded applications are immediately discarded in memory and never persisted.

---

## 📋 Prerequisites

Before running FlowState, verify your system has:
1. **Java Development Kit (JDK) 21 or newer** installed:
   ```bash
   java -version
   ```
2. **Apache Maven 3.9+** installed:
   ```bash
   mvn -version
   ```
*(No database installation is required by default! Data is stored automatically in `./data/` using Java I/O Streams).*

---

## 🗄️ Database Setup (Optional — Only if using PostgreSQL mode)

FlowState runs out-of-the-box using Java file streams. If you wish to switch to PostgreSQL mode:

### Step 1: Create Database
Open `psql` or pgAdmin and run:
```sql
CREATE DATABASE flowstate;
```

### Step 2: Configure Credentials
FlowState reads configuration from environment variables or `application.properties`.

Option A: Set Environment Variables (Recommended):
```powershell
$env:FLOWSTATE_DB_URL="jdbc:postgresql://localhost:5432/flowstate"
$env:FLOWSTATE_DB_USER="postgres"
$env:FLOWSTATE_DB_PASSWORD="your_password"
```

Option B: Create `application.properties`:
Create a file named `application.properties` in your working directory or in `src/main/resources/`:
```properties
db.url=jdbc:postgresql://localhost:5432/flowstate
db.user=postgres
db.password=your_password
db.pool.size=5
db.timeout.ms=3000
```
*(A safe reference template is provided at `src/main/resources/application.properties.example`)*.

### Step 3: Automatic Schema Migration
You **do not** need to execute manual SQL DDL commands! On startup, `DatabaseManager` automatically checks the database and executes `src/main/resources/db/schema.sql`, creating all required tables (`focus_sessions`, `application_activity`, `application_preferences`, `user_settings`) and performance indexes.

---

## 🚀 Building & Running the Application

### 1. Run All Unit & Integration Tests
```bash
mvn clean test
```
*Executes all 42 unit & integration tests across file streams, repositories, services, concurrency stress tests, and UI models.*

### 2. Launch the Desktop Application
```bash
mvn javafx:run
```
Or run the main class directly:
```bash
java -cp target/flowstate-1.0.0-SNAPSHOT.jar com.flowstate.Main
```

### 3. Graceful Connection Recovery
If PostgreSQL is unreachable at launch, FlowState will **not crash silently** or fall back to unstable in-memory storage. Instead, it displays an informative **Connection Error Recovery Screen** (`CORE-05`) detailing connection diagnostics and recommended recovery steps (e.g. verifying Windows `services.msc` or PostgreSQL port 5432) with a live **Retry Connection** button.

---

## 🎓 Academic / B.Tech CSE Viva Reference

When demonstrating FlowState for academic evaluations or technical project reviews, highlight these key design patterns:

### 1. Object-Oriented Programming (OOP)
- **Encapsulation**: Domain models (`FocusSession`, `ApplicationActivity`) maintain private state, guarded by strict transition methods (`start()`, `pause()`, `resume()`, `complete()`, `close()`).
- **Interfaces & Polymorphism**:
  - `FocusSessionRepository` & `ActivityRepository` decouple persistence from business logic, adhering to the Data Access Object (DAO) pattern.
  - `ActivityTrackingProvider` defines an abstraction for foreground window sampling, implemented by `WindowsNativeTrackingProvider` for Windows and easily extensible for Linux/macOS.
- **Observer Pattern**: `FocusSessionListener` and `ActivityChangeListener` notify UI controllers on state transitions asynchronously without tight coupling.

### 2. Multithreading & Concurrency Control
- **Non-Blocking UI Thread**: All database I/O, JNA sampling, and file exports are executed on background threads (`CompletableFuture.runAsync()` and `ScheduledExecutorService`). All UI updates are dispatched strictly via `Platform.runLater()`.
- **Clock Drift Prevention**: Wall-clock timestamp math (`Duration.between(startTime, now) - accumulatedPauseDuration`) ensures absolute precision regardless of thread sleep or window minimization.
- **Race Condition Prevention**: `AtomicReference` and thread-safe data structures (`CopyOnWriteArrayList`, `ConcurrentHashMap`) safeguard against concurrent state corruption during high-frequency tracking ticks.
- **JVM Shutdown Hook**: `LifecycleManager` registers `Runtime.getRuntime().addShutdownHook()` to terminate all executor pools and database connections cleanly upon window exit.

### 3. High-Performance Java NIO
- `ReportExportService` uses modern Java NIO (`Files.newBufferedWriter`, `StandardOpenOption`, `Path`) for memory-efficient buffered file output, combined with full RFC 4180 escaping for double quotes, commas, and newlines.

### 4. Database Integration & Security
- 100% prepared statement parameter binding prevents SQL injection attacks.
- High-efficiency connection pooling via `HikariCP` minimizes connection latency.
- Normalized schema with foreign key constraints and cascade deletions.

---

## 📄 License
This project is developed for educational and academic evaluation purposes under the MIT License.
