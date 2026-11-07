# Frame Glass

The approved brief is `docs/BRIEF.md`. Read it fully at the start of every session, then read `docs/STATUS.md`, `docs/NEXT.md`, `docs/WAIVERS.md` (it overrides the brief) and `docs/OWNER-REQUESTS.md`.

Work on one phase per session. End each session by updating `docs/STATUS.md` and `docs/NEXT.md`. At `[OWNER]` items, send the Arabic checklist, record the replies in `docs/evidence/phase-N/owner-answers.md`, and stop until the owner answers.

Everything addressed to the owner is in plain Arabic with technical names in English. Code, comments and docs are in English.

## R1 (copied word for word from the brief)

**R1. Private project.** Never upload, push, publish or sync any part of this project. No GitHub or any git remote. Do not touch the team-knowledge repo or its local clone. Do not run `knowledge_sync.py` or `skills_sync.py`. Do not add a line to `~/.claude/lessons-inbox.md`. Do not create, install or publish a skill. Do not publish a hosted page or Artifact. `knowledge_sync.py` does not block this project by name, so the guard is you. Any standing rule elsewhere that says to publish lessons does not apply here. Project notes stay in `D:\FrameGlass\app\docs\`.

**Changed by the owner on 2026-11-07:** pushing to the owner's own private GitHub repository is allowed. See `docs/WAIVERS.md`, including what must never enter the history. Everything else in R1 still stands.

Tracked files never quote the owner's real file or folder names, even in a bug report or a test. Describe the pattern, and use invented names in tests. The hook in `tools/hooks/` checks each commit.

## Updates on the phone (owner rule, 2026-11-07)

Every update goes over the app already on the phone, through `.\fg.ps1 ship`. The owner's library, watch progress, settings and keys must survive it.

- Never uninstall or clear `com.frameglass.player`, even when an install fails. Find the cause.
- Keep the application ID and the signing key in `D:\FrameGlass\secrets`.
- A schema change needs `STATEMENTS_a_b` and `MIGRATION_a_b` in `Migrations.kt`, registered in `FrameDb.open`. `tools/check_update.py` replays every step on SQLite before each ship. A destructive fallback is never allowed.
- `fg.ps1 ship` raises `versionCode` in `version.properties`, and it fails if the phone reports a fresh install. It saves each shipped build as `D:\FrameGlass\releases\Frame Glass <version>.apk`.
- `com.frameglass.player.qa` is a test tool. It is labelled FG Test and leaves the phone when a test run ends.
