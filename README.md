# Rokid-Testing

Root-level orchestration for the Rokid Workout Tracker and related tooling.

## Overview

This repository contains the Rokid Workout Tracker application - a workout tracking app designed specifically for Rokid AI Glasses. The project includes the Android application along with supporting tooling, configuration, and documentation.

## Project Structure

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

The main application is located in the `workout-tracker/` directory. See `workout-tracker/README.md` for detailed information about:
- Architecture and key files
- Navigation gestures and input methods
- Seed workouts and common exercises
- Build and installation instructions
- Feature descriptions and usage

## Key Features

- **Rokid Glasses Optimized**: Designed for the 480x640 @ 240dpi green monochrome HUD display
- **Touchpad Input**: Supports Rokid touchpad gestures (tap, double-tap, swipe)
- **Voice Recognition**: Vosk-powered speech recognition for hands-free operation
- **Session Persistence**: Workout sessions survive app closure and restoration
- **Workout Tracking**: Track sets, reps, and weights for custom and preset workouts
- **Export Functionality**: Export workout data as JSON files
- **Custom Workout Creator**: Build personalized workouts from exercise library

## Technical Stack

- **Language**: Kotlin 2.3.21
- **Android Gradle Plugin**: 8.13.2
- **Gradle**: 9.5.1
- **Database**: Room 2.8.4 (with KSP)
- **Serialization**: kotlinx-serialization 1.11.0
- **Speech Recognition**: Vosk 0.3.75 + JNA 5.19.1
- **Minimum SDK**: 31
- **Target SDK**: 36

## Key Architectural Decisions

- **Single-activity architecture** with screen-state pattern (controllers, not fragments)
- **Main-thread Room DB** with session persistence across app lifecycle
- **DND Mode**: `INTERRUPTION_FILTER_NONE` + `dispatchKeyEvent` for Rokid overlay suppression
- **Vosk Grammar + Dictation**: Dual-mode for voice recognition flexibility
- **HUD Viewport Scaling**: Via `HudViewportLayout` (480x640 portrait @ 240dpi)

## Development Tools

- **OpenCode Agents**: Configured in `.opencode/` for AI-assisted development
- **MCP Servers**: Firecrawl (web search/interact) and Exa (neural search) via configuration
- **Scripts**: Repository-level utilities in `scripts/` directory
- **Configuration**: Shared configs in `config/` directory

## Getting Started

### Prerequisites
- JDK 17+
- Android SDK (platform 36)
- `adb` for device installation

### Build and Install
```bash
# Build the debug APK
cd workout-tracker
./gradlew :app:assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.rokid.workouttracker/.MainActivity
```

## Development Workflow

This project uses OpenCode for AI-assisted development. Refer to `.opencode/` for agent configurations and commands.

## License

See individual files for licensing information.

## Contact

For questions or support, please refer to the project documentation or open an issue.