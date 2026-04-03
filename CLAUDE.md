# Flight Log App

Android flight log for pilots and passengers. Kotlin + Jetpack Compose + Material 3.

## Build
```bash
JAVA_HOME="D:/Android Studio/jbr" ./gradlew assembleDebug    # Debug APK
JAVA_HOME="D:/Android Studio/jbr" ./gradlew testDebugUnitTest # Unit tests
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Deep Docs
- `docs/harness/golden-principles.md` — 8 mechanical rules all agents must follow
- `docs/harness/pipeline.md` — Agent team, commands, quality gate, fast-track, progressive disclosure
- `docs/harness/architecture.md` — Tech stack, performance, conventions, agent legibility
- `docs/harness/state.md` — Current phase, P0 bugs, tech debt, self-improvement protocol
- `AGENTS.md` — Cross-tool agent config (agents.md open standard)

## Quick Reference
- **Architecture:** MVVM + Clean Architecture, Hilt, Room v4, Coroutines + StateFlow
- **Agents:** planner, uiux-designer, architect, developer, code-reviewer → `docs/harness/pipeline.md`
- **Golden Principles:** → `docs/harness/golden-principles.md`
- **Pipeline:** `/feature` (full) · `/review` · `/ship` · `/cleanup` · `/arch-check`
- **"Pipeline"** = always `/spawn-team` with TeamCreate + named team agents. NEVER use bare `Agent` tool directly — all work goes through the team. Even hotfixes spawn a team.
- **Quality Gate:** CRITICAL/HIGH flags block shipping. Max 3 review-fix cycles.
- **Conventions:** Push after every commit. Build APK after features. Worktrees for branches. Edge tests mandatory.
- **Struggle Log:** `.claude/struggle-log.md` — track agent failures to evolve the harness
