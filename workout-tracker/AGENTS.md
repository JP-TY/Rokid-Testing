# Workout Tracker — Rokid Glasses App

A simple workout tracking app for Rokid AI Glasses, built on the GlassKit Android pattern.

## Architecture

- **Framework**: Standard Android app (Kotlin) targeting Rokid Glasses
- **HUD**: Portrait 480×640 at 240dpi green monochrome display
- **Input**: Rokid touchpad (tap/double-tap/swipe) + phone/emulator touch fallback
- **Build**: Gradle with AGP 8.9.0, minSdk 28, targetSdk 36

## Screens

| Screen | ID | Purpose |
|--------|----|---------|
| Menu | `MENU` | Select a workout from 4 seed templates |
| Exercise | `EXERCISE` | View exercise name, adjust reps (swipe), log set (tap) |
| Timer | `TIMER` | Rest countdown with progress bar, tap to skip |
| Complete | `COMPLETE` | Workout finished summary, tap to return |

## Navigation

| Gesture | Action |
|---------|--------|
| Tap (ENTER) | Select / confirm / log set / skip timer |
| Double-tap (BACK) | Back / quit confirm / abandon confirm / return |
| Swipe forward (DPAD_DOWN) | Next item / increase reps |
| Swipe backward (DPAD_UP) | Previous item / decrease reps |

## Key Files

- `MainActivity.kt` — Activity shell, screen registration, session coordination
- `HudViewportLayout.kt` — Scales UI into Rokid 3:4 HUD viewport
- `NavigationInputMapper.kt` — Maps touchpad + touchscreen to NavigationAction
- `ScreenController.kt` — Screen ID, command, and controller contract
- `WorkoutModel.kt` — Data classes (WorkoutTemplate, ExerciseTemplate, WorkoutSession) + seed data
- `MenuScreenController.kt` — Workout list with focus navigation
- `ExerciseScreenController.kt` — Rep adjustment and set logging
- `TimerScreenController.kt` — Rest countdown with progress bar
- `CompleteScreenController.kt` — Workout done summary screen

## Seed Workouts

- Push Day (5 exercises, 90s rest)
- Pull Day (5 exercises, 90s rest)
- Leg Day (5 exercises, 120s rest)
- Full Body (4 exercises, 90s rest)

## Requirements

- JDK 17+
- Android SDK (platform 36)
- `adb` for device install

## Build & Install

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.rokid.workouttracker/.MainActivity
```

Run `gradle wrapper` in this directory if the wrapper is missing.
