# AGENTS.md

Cross-tool agent configuration following the [agents.md](https://agents.md/) open standard.

## Project
- **Name:** Flight Log App
- **Type:** Android mobile application
- **Language:** Kotlin
- **Framework:** Jetpack Compose + Material Design 3
- **Architecture:** MVVM + Clean Architecture + Hilt DI

## Build
```bash
JAVA_HOME="D:/Android Studio/jbr" ./gradlew assembleDebug
```

## Test
```bash
JAVA_HOME="D:/Android Studio/jbr" ./gradlew testDebugUnitTest
```

## Entry Points
- `CLAUDE.md` — Harness table of contents (Claude Code)
- `docs/harness/` — Deep docs: golden-principles, pipeline, architecture, state
- `.claude/commands/` — Slash commands: /feature, /review, /ship, /cleanup, /arch-check

## Golden Rules
→ `docs/harness/golden-principles.md`

## Architecture Constraints
- Dependency flow: Entity → DAO → Repository → ViewModel → UI
- No business logic in Composables
- StateFlow for state, SharedFlow for events
- All network calls must handle CancellationException
