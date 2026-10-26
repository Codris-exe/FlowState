# FlowState — Smart Distraction & Productivity Analyzer

## What This Is

FlowState is a native Java desktop application (college-level B.Tech CSE project) built with Java 21, JavaFX, and PostgreSQL that helps users understand their computer usage, measure focus sessions, identify distractions, and analyze productivity patterns. It tracks active desktop applications in real time, provides rule-based distraction detection and interruption counting during focus sessions, and delivers analytics visualizations and CSV reporting.

## Core Value

Provide a rock-solid, privacy-preserving desktop focus timer and active application tracking engine that accurately persists session metrics and calculates distraction analytics without freezing the UI or losing data across restarts.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Java 21 & Maven project setup with JavaFX (FXML + CSS), PostgreSQL JDBC driver, JNA for OS window tracking, HikariCP/connection pooling, SLF4J/Logback, and JUnit 5
- [ ] Modern UI/UX layout with dark navy/charcoal styling, persistent left sidebar navigation (Dashboard, Focus Sessions, Activity Monitor, Analytics, Session History, Reports, Settings), responsive design, and polished widgets
- [ ] PostgreSQL database schema and migrations (`focus_sessions`, `application_activity`, `application_preferences`, `user_settings`), connection management, transactions, and robust offline/reconnect error handling
- [ ] Focus session management: start/pause/resume/end timer, category selection, target durations, timestamp-based elapsed calculation, notes, and single active session enforcement
- [ ] Native background activity tracking: JNA Win32 OS integration detecting active foreground window/process, duration tracking, pause/resume tracking, exclusion lists, and privacy guardrails (no keylogging/screenshots)
- [ ] Rule-based distraction analysis: category categorization (productive, distracting, neutral), application switch tracking, interruption counting, focus ratio calculation, and streak/trend analysis
- [ ] Comprehensive analytics screen: JavaFX charts (daily/weekly/monthly focus time, application usage distribution, interruption frequency, goal completion rates, and date-range comparisons)
- [ ] Session history table with search, category/date/status filtering, session detail view, notes editing, and cascading deletion confirmation
- [ ] File operations & reporting: CSV export for sessions, application activity, and summary analytics using Java NIO and buffered I/O with conflict confirmations; settings export/import
- [ ] Settings & privacy controls: daily focus goal, theme/tracking preferences, application category manager, data cleanup, and safe data purge with confirmation
- [ ] Multithreading & thread-safety: ScheduledExecutorService for polling, background executor for database and export tasks, clean thread-safe state synchronization, and JavaFX Application Thread UI safety
- [ ] Automated testing suite with JUnit 5 covering timer logic, session lifecycle, categorization, analytics computations, CSV escaping, and configuration validation

### Out of Scope

- Web application, mobile/Android application, or browser-based dashboard — desktop Java native only
- Spring Boot or embedded web server — desktop JavaFX architecture with pure JDBC/DAO layer
- Keylogging, screenshot capturing, clipboard monitoring, browser URL history scraping, or private document reading — strict privacy adherence
- Black-box Machine Learning models for distraction scoring — transparent, user-configurable rule-based heuristics only
- Silent fallback to in-memory dummy mode if PostgreSQL is missing — explicit connection error diagnostics and recovery instructions required

## Context

- Operating System: Windows 11 (amd64) with native Win32 window APIs via JNA (Java Native Access)
- Java Runtime: Oracle JDK 25 / Java 21+ source compatibility
- Build System: Apache Maven 3.9+
- Database: PostgreSQL 18 installed locally in `C:\Program Files\PostgreSQL\18`
- Academic Context: B.Tech Computer Science & Engineering final/capstone project demonstrating Java OOP, multithreading, concurrency, JDBC, file I/O, and UI architecture

## Constraints

- **Tech Stack**: Pure Java 21+, JavaFX (FXML + CSS), JDBC with PostgreSQL, Maven — No Spring Boot
- **Concurrency**: UI thread must never block on DB or I/O; all background tasks run on managed ExecutorServices with proper shutdown hooks
- **Persistence**: PostgreSQL is mandatory for persistence; configuration via environment variables or external `.env`/`config.properties` file with safe sample templates
- **Privacy & Security**: Zero invasive monitoring; prepared statements for all queries; thread-safe data structures for active session tracking

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| JavaFX + FXML + CSS | Delivers a native, high-performance desktop GUI with responsive layouts and modern dark styling without heavy web containers | — Pending |
| JNA Win32 native tracking (`GetForegroundWindow` / `GetWindowThreadProcessId`) | Accurate active window tracking on Windows without third-party external processes or polling shell scripts | — Pending |
| JDBC DAO/Repository layer over Spring Data | Keeps desktop app lightweight, transparent, and compliant with academic B.Tech CSE requirements | — Pending |
| Rule-based distraction classification | Avoids non-deterministic ML behavior; empowers user with configurable application categories | — Pending |
| Timestamp-based session timing | Prevents clock drift when app is minimized, computer sleeps, or CPU is throttled | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-10-26 after initialization*
