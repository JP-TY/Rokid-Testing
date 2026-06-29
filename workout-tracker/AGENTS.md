# Workout Tracker — Rokid Glasses App

A simple workout tracking app for Rokid AI Glasses, built on the GlassKit Android pattern.

## Architecture

- **Framework**: Standard Android app (Kotlin) targeting Rokid Glasses
- **HUD**: Portrait 480×640 at 240dpi green monochrome display
- **Input**: Rokid touchpad (tap/double-tap/swipe) + phone/emulator touch fallback
- **Build**: Gradle with AGP 8.11.0, minSdk 31, targetSdk 36, Kotlin 2.1.0, Room 2.7.1

## Screens

| Screen | ID | Purpose |
|--------|----|---------|
| Menu | `MENU` | Select a workout from seed + custom templates |
| Exercise List | `EXERCISE_LIST` | Browse exercises, end workout, or delete session |
| Exercise | `EXERCISE` | View exercise name, adjust reps (swipe), log set (tap) |
| Timer | `TIMER` | Shows "Set X of Y" for current exercise, tap to continue |
| Complete | `COMPLETE` | Workout finished summary, tap to menu, double-tap to export |
| History | `HISTORY` | Past completed workouts |
| Custom Workout | `CUSTOM_WORKOUT` | Create custom workout with common exercises quick-pick |

## Navigation

| Gesture | Action |
|---------|--------|
| Tap (SELECT) | Select / confirm / log set / advance timer / save workout |
| Double-tap (BACK) | Back / discard confirm / end confirm / delete confirm / quit |
| Swipe forward (DPAD_DOWN) | Next item / increase reps / browse chars / browse common exercises |
| Swipe backward (DPAD_UP) | Previous item / decrease reps / browse chars / browse common exercises |

## Key Features

### Custom Workout Creation
1. Name the workout using the character picker (swipe → tap letters, double-tap to confirm)
2. Set number of exercises (swipe → tap)
3. For each exercise: pick from common exercises list, or select "Custom..." to type a name
4. Set target reps and default weight per exercise
5. Confirm save

### Delete Custom Workout (Menu screen)
- Navigate to a custom template row
- Double-tap (BACK) → "Delete [name]?" confirmation appears
- Double-tap again → permanent deletion

### Timer Screen
- Shows only "Set X of Y" for the current exercise
- "All sets complete!" when current exercise is done
- Single tap advances to the next screen

### Workout Session Persistence
- Active sessions survive app leave/return (idle battery save, accidental close)
- Session restored from Room DB on `onResume()` if in-memory state is null
- `FLAG_KEEP_SCREEN_ON` prevents Rokid system screen timeout during workouts
- Aggressive immersive mode + `setOnSystemUiVisibilityChangeListener` suppresses system overlays

### Session Management (Exercise List)
- **Finish Workout**: saves to history as COMPLETED, then shows menu
- **Delete Session**: double-tap on BACK (arms) → double-tap again (deletes from DB, no history trace)
  - Also accessible via "Delete Session" action row at bottom of exercise list

### Export Workout (Complete screen)
1. Complete a workout → Complete screen appears
2. Double-tap (BACK) to arm export ("Double-tap again to export")
3. Double-tap again → JSON file saved to `Downloads/WorkoutTracker/` directory
4. File uses the naming format: `workout_YYYY-MM-DD_HHmmss.json`
5. Status feedback: "Exported!" shown on success, "Export failed" on error

### Idle Timeout
- 5-minute idle timeout (300s) before auto-return to menu
- Timer is cancelled during workouts (FLAG_KEEP_SCREEN_ON)
- Timeout resets on any user interaction

## Seed Workouts

- Push Day (5 exercises, 90s rest)
- Pull Day (5 exercises, 90s rest)
- Leg Day (5 exercises, 120s rest)
- Full Body (4 exercises, 90s rest)
- Upper (4 exercises, 60s rest)
- Lower (4 exercises, 90s rest)

## Common Exercises (Quick-Pick)

The custom workout screen offers these common exercises for quick selection:
Bench Press, Squat, Deadlift, Overhead Press, Barbell Row, Pull Up, Push Up, Dumbbell Curl, Triceps Extension, Leg Press, Lat Pulldown, Dumbbell Fly, Shoulder Raise, Plank, Crunch

## Key Files

- `MainActivity.kt` — Activity shell, screen registration, session coordination, idle timer, FLAG_KEEP_SCREEN_ON, delete session
- `HudViewportLayout.kt` — Scales UI into Rokid 3:4 HUD viewport
- `NavigationInputMapper.kt` — Maps touchpad + touchscreen to NavigationAction
- `ScreenController.kt` — Screen ID, command, and controller contract
- `WorkoutModel.kt` — Data classes + seed data
- `MenuScreenController.kt` — Workout list with focus navigation + delete custom template
- `ExerciseListScreenController.kt` — Exercise list with end/delete actions
- `ExerciseScreenController.kt` — Rep adjustment and set logging
- `TimerScreenController.kt` — Sets-remaining display (no countdown, tap to continue)
- `CompleteScreenController.kt` — Workout done summary + export
- `CustomWorkoutScreen.kt` — Create workout with common exercises quick-pick + character picker
- `HistoryScreenController.kt` — Past completed workouts
- `WorkoutDatabase.kt` — Room database, DAOs, repository

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
