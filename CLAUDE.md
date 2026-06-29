# Rokid-Testing — Project Index

Root-level orchestration for the Rokid Workout Tracker and related tooling.

## Layout

```
Rokid-Testing/
├── .opencode/        # OpenCode agent + command definitions
├── .mcp.json         # MCP server configs (firecrawl)
├── opencode.json     # OpenCode config (exa + firecrawl MCPs)
├── config/           # Shared configs (mcporter.json)
├── workout-tracker/  # Android app source (Kotlin + Gradle)
└── scripts/          # Repo-level scripts (harness-audit.js)
```

## Primary App: workout-tracker/

See `workout-tracketailed architecture,key files, navigation gestures, seed workouts, build/install steps, and all feature descriptions.

### Quick Reference

| Action | Command |
|--------|---------|
| Build | `cd workout-tracker && ./gradlew :app:assembleDebug` |
| Lint | `cd workout-tracker && ./gradlew :app:lint` |
| Test | `cd workout-tracker && ./gradlew :app:testDebugUnitTest` |
| Install | `adb install -r workout-tracker/app/build/outputs/apk/debug/app-debug.apk` |
| Launch | `adb shell am start -n com.rokid.workouttracker/.MainActivity` |

### Tech Stack

- Kotlin 2.3.21, AGP 8.13.2, Gradle 9.5.1
- Room 2.8.4 (KSP), kotlinx-serialization 1.11.0
- Vosk 0.3.75 + JNA 5.19.1 (speech recognition)
- minSdk 31, targetSdk 36

### Key Architectural Decisions

- Single-activity architecture with screen-state pattern (controllers, not fragments)
- Main-thread Room DB with session persistence across app lifecycle
- DND (INTERRUPTION_FILTER_NONE) + dispatchKeyEvent for Rokid overlay suppression
- Vosk grammar + dictation dual-mode for voice recognition
- HUD viewport scaling via `HudViewportLayout` (480x640 portrait @ 240dpi)

## Scripts

- `scripts/harness-audit.js` — Determinharness quality scorer (25-05-19)

## MCP Servers

- **Firecrawl**: web search + interact (via `opencode.json` + `.mcp.json`)
- **Exa**: neural search (via `opencode.json`)

## Editor Config

`.editorconfig` at `workout-tracker/.editorconfig`: 4-space indent, 160-char line length, final newline, ktlint-compatible.
