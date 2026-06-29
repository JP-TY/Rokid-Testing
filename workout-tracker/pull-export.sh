#!/bin/bash
# Pull the latest workout export from the Rokid device
# Run this after exporting from the app (Complete screen -> double-tap -> double-tap)
# The file is saved as: workout_latest.json

adb pull "/storage/emulated/0/Download/workout_latest.json" .
echo "Pulled to: $(pwd)/workout_latest.json"
