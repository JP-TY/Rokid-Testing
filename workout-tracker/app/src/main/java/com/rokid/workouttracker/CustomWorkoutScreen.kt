package com.rokid.workouttracker

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.TextView

internal class CustomWorkoutScreen(
    panelView: View
) : ViewScreenController(ScreenId.CUSTOM_WORKOUT, panelView) {

    private val promptView: TextView = panelView.findViewById(R.id.customWorkoutPromptView)
    private val hintView: TextView = panelView.findViewById(R.id.customWorkoutVoiceHintView)
    private val resultView: TextView = panelView.findViewById(R.id.customWorkoutResultView)
    private val errorView: TextView = panelView.findViewById(R.id.customWorkoutErrorView)
    private val fgColor: Int
    private val accentColor: Int

    private val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ "
    private val specialCount = 1
    private var charIndex = 0

    private data class PickerEntry(val label: String, val isSpecial: Boolean, val action: Action)
    private enum class Action { ADD_CHAR, BACKSPACE }
    private enum class Step { NAME, COUNT, EXERCISES, SAVE }

    private var step = Step.NAME
    private var workoutName = ""
    private var exerciseCount = 1

    private var currentExerciseIndex = 0
    private var currentExerciseName = ""
    private var currentReps = 10
    private var currentWeight = 0

    private val exerciseNames = mutableListOf<String>()
    private val exerciseRepsList = mutableListOf<Int>()
    private val exerciseWeightList = mutableListOf<Int>()

    private enum class ExerciseSubStep { NAME, REPS, WEIGHT }
    private var exerciseSubStep = ExerciseSubStep.NAME

    init {
        val ctx = panelView.context
        fgColor = ctx.getColor(R.color.hud_foreground)
        accentColor = ctx.getColor(R.color.hud_accent)
        errorView.setTextColor(accentColor)
    }

    override fun onEnter() {
        step = Step.NAME
        workoutName = ""
        exerciseCount = 1
        resetExerciseAccumulators()
        charIndex = 0
        resultView.visibility = View.GONE
        errorView.visibility = View.GONE
        promptView.text = panelView.context.getString(R.string.custom_workout_name_prompt)
        hintView.text = "Swipe: browse  Tap: select  Double-tap: confirm"
        hintView.visibility = View.VISIBLE
        showPicker()
    }

    override fun onExit() {}

    override fun render() {}

    private fun resetExerciseAccumulators() {
        currentExerciseIndex = 0
        currentExerciseName = ""
        currentReps = 10
        currentWeight = 0
        exerciseNames.clear()
        exerciseRepsList.clear()
        exerciseWeightList.clear()
        exerciseSubStep = ExerciseSubStep.NAME
    }

    private fun startNextExercise() {
        currentExerciseName = ""
        currentReps = 10
        currentWeight = 0
        exerciseSubStep = ExerciseSubStep.NAME
        charIndex = 0
        promptView.text = "Exercise ${currentExerciseIndex + 1} of $exerciseCount: pick name"
        showPicker()
    }

    private fun currentEntry(): PickerEntry {
        if (charIndex < chars.length) {
            val c = chars[charIndex]
            val label = if (c == ' ') "⎵ Space" else "Letter: $c"
            return PickerEntry(label, false, Action.ADD_CHAR)
        }
        return PickerEntry("← Backspace", true, Action.BACKSPACE)
    }

    private fun showPicker() {
        errorView.visibility = View.GONE
        val resultText = when {
            step == Step.COUNT || step == Step.SAVE -> ""
            step == Step.EXERCISES && exerciseSubStep != ExerciseSubStep.NAME -> ""
            else -> {
                val entry = currentEntry()
                val prefix = when (step) {
                    Step.NAME -> "Name: $workoutName"
                    Step.EXERCISES -> "Ex ${currentExerciseIndex + 1}: $currentExerciseName"
                    else -> ""
                }
                "$prefix\n> ${entry.label} <"
            }
        }
        resultView.text = resultText
        resultView.visibility = if (resultText.isEmpty()) View.GONE else View.VISIBLE
        resultView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

        when (step) {
            Step.COUNT -> {
                promptView.text = "Exercises: $exerciseCount  (tap to confirm)"
                hintView.text = "Swipe: change count  Tap: confirm"
            }
            Step.SAVE -> {
                val sb = StringBuilder("\"$workoutName\"")
                exerciseNames.forEachIndexed { i, name ->
                    sb.append("\n  • $name: ${exerciseRepsList[i]} reps × ${exerciseWeightList[i]}kg")
                }
                promptView.text = sb.toString()
                hintView.text = "Tap: save  Double-tap: cancel"
            }
            Step.EXERCISES -> when (exerciseSubStep) {
                ExerciseSubStep.NAME -> {
                    promptView.text = "Exercise ${currentExerciseIndex + 1} of $exerciseCount: pick name"
                    hintView.text = "Swipe: browse  Tap: select  Double-tap: confirm"
                }
                ExerciseSubStep.REPS -> {
                    promptView.text = "Exercise ${currentExerciseIndex + 1}: target reps"
                    hintView.text = "Swipe: adjust  Tap: confirm"
                    resultView.text = "Reps: $currentReps"
                    resultView.visibility = View.VISIBLE
                }
                ExerciseSubStep.WEIGHT -> {
                    promptView.text = "Exercise ${currentExerciseIndex + 1}: default weight"
                    hintView.text = "Swipe: adjust  Tap: confirm"
                    resultView.text = "Weight: ${currentWeight} kg"
                    resultView.visibility = View.VISIBLE
                }
            }
            Step.NAME -> {} // hint already set in onEnter
        }
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        return when (step) {
            Step.NAME -> handleNameAction(action)
            Step.COUNT -> handleCountAction(action)
            Step.EXERCISES -> handleExercisesAction(action)
            Step.SAVE -> handleSaveAction(action)
        }
    }

    private fun handleNameAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                when (currentEntry().action) {
                    Action.ADD_CHAR -> {
                        workoutName += chars[charIndex]
                        showPicker()
                    }
                    Action.BACKSPACE -> {
                        if (workoutName.isNotEmpty()) workoutName = workoutName.dropLast(1)
                        showPicker()
                    }
                }
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                if (workoutName.isNotBlank()) {
                    step = Step.COUNT
                    showPicker()
                    ScreenCommand.Stay
                } else {
                    ScreenCommand.Open(ScreenId.MENU)
                }
            }
            NavigationAction.NEXT -> {
                val max = chars.length + specialCount - 1
                charIndex = (charIndex + 1).coerceAtMost(max)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                charIndex = (charIndex - 1).coerceAtLeast(0)
                showPicker()
                ScreenCommand.Stay
            }
        }
    }

    private fun handleCountAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                step = Step.EXERCISES
                resetExerciseAccumulators()
                startNextExercise()
                ScreenCommand.Stay
            }
            NavigationAction.NEXT -> {
                exerciseCount = (exerciseCount + 1).coerceAtMost(20)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                exerciseCount = (exerciseCount - 1).coerceAtLeast(1)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> ScreenCommand.Open(ScreenId.MENU)
        }
    }

    private fun handleExercisesAction(action: NavigationAction): ScreenCommand {
        return when (exerciseSubStep) {
            ExerciseSubStep.NAME -> handleExerciseNameAction(action)
            ExerciseSubStep.REPS -> handleExerciseRepsAction(action)
            ExerciseSubStep.WEIGHT -> handleExerciseWeightAction(action)
        }
    }

    private fun confirmCurrentExercise() {
        exerciseNames.add(currentExerciseName.trim())
        exerciseRepsList.add(currentReps)
        exerciseWeightList.add(currentWeight)
        currentExerciseIndex++
    }

    private fun handleExerciseNameAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                when (currentEntry().action) {
                    Action.ADD_CHAR -> {
                        currentExerciseName += chars[charIndex]
                        showPicker()
                    }
                    Action.BACKSPACE -> {
                        if (currentExerciseName.isNotEmpty()) currentExerciseName = currentExerciseName.dropLast(1)
                        showPicker()
                    }
                }
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                if (currentExerciseName.isNotBlank()) {
                    exerciseSubStep = ExerciseSubStep.REPS
                    showPicker()
                    ScreenCommand.Stay
                } else if (currentExerciseIndex == 0) {
                    step = Step.COUNT
                    showPicker()
                    ScreenCommand.Stay
                } else {
                    currentExerciseIndex--
                    currentExerciseName = exerciseNames.removeLastOrNull() ?: ""
                    currentReps = exerciseRepsList.removeLastOrNull() ?: 10
                    currentWeight = exerciseWeightList.removeLastOrNull() ?: 0
                    exerciseSubStep = ExerciseSubStep.WEIGHT
                    showPicker()
                    ScreenCommand.Stay
                }
            }
            NavigationAction.NEXT -> {
                val max = chars.length + specialCount - 1
                charIndex = (charIndex + 1).coerceAtMost(max)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                charIndex = (charIndex - 1).coerceAtLeast(0)
                showPicker()
                ScreenCommand.Stay
            }
        }
    }

    private fun handleExerciseRepsAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                exerciseSubStep = ExerciseSubStep.WEIGHT
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                exerciseSubStep = ExerciseSubStep.NAME
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.NEXT -> {
                currentReps = (currentReps + 1).coerceAtMost(30)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                currentReps = (currentReps - 1).coerceAtLeast(1)
                showPicker()
                ScreenCommand.Stay
            }
        }
    }

    private fun handleExerciseWeightAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                confirmCurrentExercise()
                if (currentExerciseIndex >= exerciseCount) {
                    step = Step.SAVE
                    showPicker()
                } else {
                    startNextExercise()
                }
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                exerciseSubStep = ExerciseSubStep.REPS
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.NEXT -> {
                currentWeight = (currentWeight + 5).coerceAtMost(500)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                currentWeight = (currentWeight - 5).coerceAtLeast(0)
                showPicker()
                ScreenCommand.Stay
            }
        }
    }

    private fun handleSaveAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                saveWorkout()
                ScreenCommand.Open(ScreenId.MENU)
            }
            NavigationAction.BACK -> ScreenCommand.Open(ScreenId.MENU)
            else -> ScreenCommand.Stay
        }
    }

    private fun saveWorkout() {
        if (workoutName.isBlank() || exerciseNames.isEmpty()) return
        val activity = panelView.context as MainActivity
        val repo = activity.getRepository()
        val exercises = exerciseNames.mapIndexed { i, name ->
            ExerciseTemplate(
                id = slugify(name),
                name = name.trim(),
                sets = 3,
                targetReps = exerciseRepsList.getOrElse(i) { 10 },
                defaultWeight = exerciseWeightList.getOrElse(i) { 0 }.toFloat()
            )
        }
        val template = WorkoutTemplate(name = workoutName.trim(), exercises = exercises, restSeconds = 90)
        repo.saveCustomTemplate(template)
        activity.refreshCustomWorkouts()
    }

    override fun navigationHint(context: Context): String {
        return context.getString(R.string.nav_custom_workout)
    }
}
