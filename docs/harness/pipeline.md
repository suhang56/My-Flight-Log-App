# Agent Pipeline

## Team
5 agents at `~/.claude/agents/`:
- **planner** (Sonnet) — specs, task breakdown
- **uiux-designer** (Sonnet) — Material 3 screens, components
- **architect** (Sonnet) — architecture decisions, modularization
- **developer** (Opus) — writes Kotlin/Compose code
- **code-reviewer** (Opus) — correctness, security, performance

## Commands
- `/feature <name> <desc>` — full 6-phase pipeline (spec → design → implement → review → discuss → ship)
- `/review` — dispatch code-reviewer only
- `/ship` — commit + push + merge + APK (hard prerequisites)
- `/cleanup` — entropy management scan
- `/arch-check` — architecture fitness function

## Pipeline Rules
- "pipeline" = `/spawn-team` with TeamCreate, never manual investigation
- ALL agent work MUST go through a team (TeamCreate → named agents). NEVER use bare `Agent` tool directly — even hotfixes and single-file fixes spawn a team first
- Only spawn the 5 defined agents — never generic Explore or general-purpose
- Bug fix: **developer** + **code-reviewer**
- Feature: **planner** → **developer** → **code-reviewer**
- Architecture: **architect**
- UI/screens: **uiux-designer**

## Agent Plan Mode
Every agent uses plan mode first when dispatched via `/feature`:
- Outline approach in 5-10 bullets → send to team lead → proceed after approval
- Prevents wasted tokens on wrong-direction work

## Quality Gate
- Code reviewer tags issues: CRITICAL / HIGH / MEDIUM / LOW
- Shipping BLOCKED if ANY CRITICAL or HIGH flags in latest review
- `/ship` verifies `.claude/code-reviews.md` verdict before proceeding
- Max 3 review-fix cycles before escalating to user

## Fast-Track (Low-Risk Changes)
Skip Phase 2 (design+arch) and Phase 5 (discussion) when ALL true:
- ≤3 files changed
- No new dependencies or architecture changes
- Only MEDIUM/LOW review flags
- NOT security, DB migration, API, or external dependency changes

## Progressive Disclosure
Agents receive context in layers, not all at once:
- **Layer 0:** CLAUDE.md TOC + task description (~500 tokens)
- **Layer 1:** Read relevant `docs/harness/*.md` based on task type
- **Layer 2:** Read specific source files when implementing
- **Layer 3:** Spawn sub-agent for deep-dive into unfamiliar code
