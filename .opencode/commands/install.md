# /install

Install the debug APK to a connected device and launch.

```bash
cd /home/jpty/Rokid-Testing/workout-tracker && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.rokid.workouttracker/.MainActivity
```
