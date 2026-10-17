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

### Optional web research with Parallel

Use [Parallel Search MCP](https://docs.parallel.ai/integrations/mcp/search-mcp) in OpenCode to find current Android or Rokid development references and fetch pages for context. The anonymous endpoint provides free web search in Fast mode and page fetching without an account or API key, with usage limits for light use.

To opt in, merge this fragment into the root `opencode.json`. Add `parallel` alongside the existing `exa` and `firecrawl` entries under `mcp`; keep those entries and your other settings:

```json
{
  "mcp": {
    "parallel": {
      "type": "remote",
      "url": "https://search.parallel.ai/mcp",
      "enabled": true,
      "oauth": false,
      "headers": {
        "User-Agent": "Rokid-Testing/Parallel-MCP (+https://github.com/JP-TY/Rokid-Testing)"
      }
    }
  }
}
```

`oauth: false` keeps this connection anonymous. See [OpenCode's remote MCP setup](https://opencode.ai/docs/mcp-servers/#remote) for configuration options.

From the repository root, check the connection, then start a new OpenCode session:

```bash
opencode mcp list
opencode
```

The list should show `parallel` as connected. Try this prompt in the session:

> Use the parallel MCP tools to search for official Android documentation on FLAG_KEEP_SCREEN_ON, then fetch the relevant page. Explain when the flag keeps the display awake and include the source URLs.

The server offers `web_search` and `web_fetch`; OpenCode prefixes MCP tool names with the server name. These tools support development research, not web access inside the workout app. To turn the connection off, set `mcp.parallel.enabled` to `false` and restart OpenCode.

## License

See individual files for licensing information.

## Contact

For questions or support, please refer to the project documentation or open an issue.
