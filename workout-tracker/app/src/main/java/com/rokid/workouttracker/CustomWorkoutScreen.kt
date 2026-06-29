package com.rokid.workouttracker

import android.content.Context
import android.graphics.Typeface
import android.util.Log
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
    private val specialCount = 2
    private var charIndex = 0

    private val commonExercises = listOf(
        "Bench Press", "Squat", "Deadlift", "Overhead Press",
        "Barbell Row", "Pull Up", "Push Up", "Dumbbell Curl",
        "Triceps Extension", "Leg Press", "Lat Pulldown",
        "Dumbbell Fly", "Shoulder Raise", "Plank", "Crunch"
    )

    private data class PickerEntry(val label: String, val isSpecial: Boolean, val action: Action)
    private enum class Action { ADD_CHAR, BACKSPACE, VOICE, SELECT_COMMON }
    private enum class Step { NAME, COUNT, EXERCISES, SAVE }
    private enum class NameMode { VOICE, PICKER }
    private var nameMode = NameMode.VOICE

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

    private enum class ExerciseSubStep { COMMON, NAME, REPS, WEIGHT }
    private var exerciseSubStep = ExerciseSubStep.COMMON
    private var commonPickIndex = 0

    private var dictationName = ""

    init {
        val ctx = panelView.context
        fgColor = ctx.getColor(R.color.hud_foreground)
        accentColor = ctx.getColor(R.color.hud_accent)
        errorView.setTextColor(accentColor)
    }

    override fun onEnter() {
        step = Step.NAME
        workoutName = ""
        dictationName = ""
        exerciseCount = 1
        resetExerciseAccumulators()
        charIndex = 0
        nameMode = NameMode.VOICE
        resultView.visibility = View.GONE
        errorView.visibility = View.GONE
        startVoiceName()
    }

    private fun startVoiceName() {
        nameMode = NameMode.VOICE
        val activity = panelView.context as MainActivity
        promptView.text = panelView.context.getString(R.string.custom_prompt_speak)
        hintView.text = panelView.context.getString(R.string.custom_hint_dictation)
        hintView.visibility = View.VISIBLE
        resultView.visibility = View.GONE
        activity.enterDictationForName { text, isFinal ->
            val word = text.trim().lowercase()
            if (word.isNotEmpty()) {
                when (word) {
                    "done", "confirm", "finish" -> {
                        if (workoutName.isNotBlank()) {
                            activity.exitDictation()
                            dictationName = ""
                            showPicker()
                        }
                    }
                    "delete", "backspace" -> {
                        workoutName = workoutName.trimEnd()
                        val lastSpace = workoutName.lastIndexOf(' ')
                        if (lastSpace >= 0) {
                            workoutName = workoutName.substring(0, lastSpace)
                        } else {
                            workoutName = ""
                        }
                        updateNameDisplay()
                    }
                    "space" -> {
                        workoutName += " "
                        updateNameDisplay()
                    }
                    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
                    "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z" -> {
                        workoutName += word
                        updateNameDisplay()
                    }
                    else -> {
                        if (workoutName.isNotEmpty() && !workoutName.endsWith(" ")) {
                            workoutName += " "
                        }
                        workoutName += word.replaceFirstChar { it.uppercase() }
                        updateNameDisplay()
                    }
                }
            }
        }
    }

    private fun showVoiceNameResult() {
        val display = workoutName.ifEmpty { dictationName }
        resultView.text = if (display.isNotEmpty()) "> $display <" else ""
        resultView.visibility = if (display.isNotEmpty()) View.VISIBLE else View.GONE
        promptView.text = panelView.context.getString(R.string.custom_prompt_speak)
        hintView.text = panelView.context.getString(R.string.custom_hint_dictation)
    }

    private fun updateNameDisplay() {
        dictationName = workoutName
        resultView.text = if (workoutName.isNotEmpty()) "> $workoutName <" else ""
        resultView.visibility = if (workoutName.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun switchToPicker() {
        nameMode = NameMode.PICKER
        val activity = panelView.context as MainActivity
        activity.exitDictation()
        dictationName = ""
        charIndex = 0
        showPicker()
    }

    override fun onExit() {
        (panelView.context as MainActivity).exitDictation()
        dictationName = ""
    }

    override fun render() {}

    private fun resetExerciseAccumulators() {
        currentExerciseIndex = 0
        currentExerciseName = ""
        currentReps = 10
        currentWeight = 0
        exerciseNames.clear()
        exerciseRepsList.clear()
        exerciseWeightList.clear()
        exerciseSubStep = ExerciseSubStep.COMMON
    }

    private fun startNextExercise() {
        currentExerciseName = ""
        currentReps = 10
        currentWeight = 0
        exerciseSubStep = ExerciseSubStep.COMMON
        charIndex = 0
        commonPickIndex = 0
        promptView.text = panelView.context.getString(R.string.custom_prompt_exercise_pick, currentExerciseIndex + 1, exerciseCount)
        showPicker()
    }

    private fun currentEntry(): PickerEntry {
        if (charIndex < chars.length) {
            val c = chars[charIndex]
            val label = if (c == ' ') "⎵ Space" else "Letter: $c"
            return PickerEntry(label, false, Action.ADD_CHAR)
        }
        if (charIndex == chars.length) {
            return PickerEntry("← Backspace", true, Action.BACKSPACE)
        }
        return PickerEntry("Voice", false, Action.VOICE)
    }

    private fun showPicker() {
        errorView.visibility = View.GONE
        val resultText = when {
            step == Step.COUNT || step == Step.SAVE -> ""
            step == Step.EXERCISES && exerciseSubStep == ExerciseSubStep.COMMON -> ""
            step == Step.EXERCISES && exerciseSubStep != ExerciseSubStep.NAME -> ""
            step == Step.EXERCISES && exerciseSubStep == ExerciseSubStep.NAME -> {
                val entry = currentEntry()
                val prefix = "Ex ${currentExerciseIndex + 1}: $currentExerciseName"
                "$prefix\n> ${entry.label} <"
            }
            step == Step.NAME && nameMode == NameMode.PICKER -> {
                val entry = currentEntry()
                "Name: $workoutName\n> ${entry.label} <"
            }
            step == Step.NAME && nameMode == NameMode.VOICE -> {
                val display = workoutName.ifEmpty { dictationName }
                if (display.isNotEmpty()) "Name: $display" else ""
            }
            else -> ""
        }
        resultView.text = resultText
        resultView.visibility = if (resultText.isEmpty()) View.GONE else View.VISIBLE
        resultView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

        when (step) {
            Step.COUNT -> {
                promptView.text = panelView.context.getString(R.string.custom_prompt_exercises, exerciseCount)
                hintView.text = panelView.context.getString(R.string.custom_hint_count)
            }
            Step.SAVE -> {
                val sb = StringBuilder("\"$workoutName\"")
                exerciseNames.forEachIndexed { i, name ->
                    sb.append("\n  • $name: ${exerciseRepsList[i]} reps × ${exerciseWeightList[i]}kg")
                }
                promptView.text = sb.toString()
                hintView.text = panelView.context.getString(R.string.custom_hint_save)
            }
            Step.EXERCISES -> when (exerciseSubStep) {
                ExerciseSubStep.COMMON -> {
                    val exName = commonExercises.getOrNull(commonPickIndex) ?: "Custom..."
                    promptView.text = panelView.context.getString(R.string.custom_prompt_exercise_choose, currentExerciseIndex + 1, exerciseCount)
                    hintView.text = panelView.context.getString(R.string.custom_hint_type_custom)
                    resultView.text = if (commonPickIndex < commonExercises.size) {
                        "> $exName <"
                    } else {
                        "> Custom...  (type name) <"
                    }
                    resultView.visibility = View.VISIBLE
                }
                ExerciseSubStep.NAME -> {
                    promptView.text = panelView.context.getString(R.string.custom_prompt_exercise_type_name, currentExerciseIndex + 1, exerciseCount)
                    hintView.text = panelView.context.getString(R.string.custom_hint_picker)
                }
                ExerciseSubStep.REPS -> {
                    promptView.text = panelView.context.getString(R.string.custom_prompt_reps, currentExerciseIndex + 1)
                    hintView.text = panelView.context.getString(R.string.custom_hint_adjust)
                    resultView.text = panelView.context.getString(R.string.custom_result_reps, currentReps)
                    resultView.visibility = View.VISIBLE
                }
                ExerciseSubStep.WEIGHT -> {
                    promptView.text = panelView.context.getString(R.string.custom_prompt_weight, currentExerciseIndex + 1)
                    hintView.text = panelView.context.getString(R.string.custom_hint_adjust)
                    resultView.text = panelView.context.getString(R.string.custom_result_weight, currentWeight)
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
        return when (nameMode) {
            NameMode.VOICE -> handleVoiceNameAction(action)
            NameMode.PICKER -> handlePickerNameAction(action)
        }
    }

    private fun handleVoiceNameAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                if (workoutName.isNotBlank()) {
                    step = Step.COUNT
                    showPicker()
                } else {
                    switchToPicker()
                }
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                if (dictationName.isNotEmpty()) {
                    dictationName = ""
                    (panelView.context as MainActivity).exitDictation()
                    showVoiceNameResult()
                } else if (workoutName.isNotBlank()) {
                    workoutName = ""
                    showVoiceNameResult()
                } else {
                    ScreenCommand.Open(ScreenId.MENU)
                }
                ScreenCommand.Stay
            }
            NavigationAction.NEXT -> {
                if (workoutName.isBlank() && dictationName.isBlank()) {
                    switchToPicker()
                } else {
                    step = Step.COUNT
                    showPicker()
                }
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                switchToPicker()
                ScreenCommand.Stay
            }
        }
    }

    private fun handlePickerNameAction(action: NavigationAction): ScreenCommand {
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
                    Action.VOICE -> {
                        startVoiceName()
                        ScreenCommand.Stay
                    }
                    Action.SELECT_COMMON -> {}
                }
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                if (workoutName.isNotBlank()) {
                    step = Step.COUNT
                    showPicker()
                } else {
                    ScreenCommand.Open(ScreenId.MENU)
                }
                ScreenCommand.Stay
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
            ExerciseSubStep.COMMON -> handleExerciseCommonAction(action)
            ExerciseSubStep.NAME -> handleExerciseNameAction(action)
            ExerciseSubStep.REPS -> handleExerciseRepsAction(action)
            ExerciseSubStep.WEIGHT -> handleExerciseWeightAction(action)
        }
    }

    private fun handleExerciseCommonAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                if (commonPickIndex < commonExercises.size) {
                    currentExerciseName = commonExercises[commonPickIndex]
                    exerciseSubStep = ExerciseSubStep.REPS
                } else {
                    exerciseSubStep = ExerciseSubStep.NAME
                    charIndex = 0
                }
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                if (currentExerciseIndex == 0) {
                    step = Step.COUNT
                    showPicker()
                } else {
                    currentExerciseIndex--
                    currentExerciseName = exerciseNames.removeLastOrNull() ?: ""
                    currentReps = exerciseRepsList.removeLastOrNull() ?: 10
                    currentWeight = exerciseWeightList.removeLastOrNull() ?: 0
                    exerciseSubStep = ExerciseSubStep.WEIGHT
                    showPicker()
                }
                ScreenCommand.Stay
            }
            NavigationAction.NEXT -> {
                commonPickIndex = (commonPickIndex + 1).coerceAtMost(commonExercises.size)
                showPicker()
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                commonPickIndex = (commonPickIndex - 1).coerceAtLeast(0)
                showPicker()
                ScreenCommand.Stay
            }
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
                    Action.VOICE -> {
                        exerciseSubStep = ExerciseSubStep.NAME
                        showPicker()
                        ScreenCommand.Stay
                    }
                    Action.SELECT_COMMON -> {}
                }
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> {
                if (currentExerciseName.isNotBlank()) {
                    exerciseSubStep = ExerciseSubStep.REPS
                    showPicker()
                } else {
                    exerciseSubStep = ExerciseSubStep.COMMON
                    commonPickIndex = 0
                    showPicker()
                }
                ScreenCommand.Stay
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
