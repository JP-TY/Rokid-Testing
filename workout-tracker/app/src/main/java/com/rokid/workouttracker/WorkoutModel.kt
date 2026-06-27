package com.rokid.workouttracker

import kotlinx.serialization.Serializable

enum class WeightUnit { LB, KG }

data class ExerciseTemplate(
    val id: String,
    val name: String,
    val sets: Int,
    val targetReps: Int,
    val defaultWeight: Float = 0f,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val restSeconds: Int? = null
)

data class WorkoutTemplate(
    val name: String,
    val exercises: List<ExerciseTemplate>,
    val restSeconds: Int
)

data class LoggedSet(
    val reps: Int,
    val weight: Float = 0f,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExerciseProgress(
    val template: ExerciseTemplate,
    val sets: MutableList<LoggedSet>
) {
    val totalSets: Int get() = template.sets
    val doneSets: Int get() = sets.size
    val isComplete: Boolean get() = sets.size >= template.sets
}

enum class SessionStatus { ACTIVE, COMPLETED, ABANDONED }

class WorkoutSession(
    val template: WorkoutTemplate,
    val exercises: MutableList<ExerciseProgress>,
    val workoutStartTime: Long = System.currentTimeMillis(),
    var id: Long = 0
) {
    var currentExerciseIndex: Int = 0
    var currentRepAdjustment: Int = template.exercises[0].targetReps
    var currentWeightAdjustment: Float = template.exercises[0].defaultWeight
    var lastLoggedSet: LoggedSet? = null
    var status: SessionStatus = SessionStatus.ACTIVE

    val currentExercise: ExerciseProgress
        get() = exercises[currentExerciseIndex]

    val allExercisesComplete: Boolean
        get() = exercises.all { it.isComplete }

    fun getElapsedSeconds(): Long {
        return (System.currentTimeMillis() - workoutStartTime) / 1000
    }

    fun logCurrentSet(): LoggedSet {
        val exercise = currentExercise
        val loggedSet = LoggedSet(
            reps = currentRepAdjustment,
            weight = currentWeightAdjustment,
            weightUnit = exercise.template.weightUnit
        )
        exercise.sets.add(loggedSet)
        lastLoggedSet = loggedSet
        return loggedSet
    }

    fun moveToExercise(index: Int) {
        if (index in exercises.indices) {
            currentExerciseIndex = index
            val ex = exercises[index]
            currentRepAdjustment = ex.template.targetReps
            currentWeightAdjustment = ex.template.defaultWeight
        }
    }

    fun totalVolume(): Float {
        var total = 0.0
        for (ep in exercises) {
            for (s in ep.sets) {
                total += s.reps.toDouble() * s.weight.toDouble()
            }
        }
        return total.toFloat()
    }

    fun totalSetsLogged(): Int {
        return exercises.sumOf { it.doneSets }
    }

    companion object {
        fun create(template: WorkoutTemplate): WorkoutSession {
            val exercises = template.exercises.map { ex ->
                ExerciseProgress(
                    template = ex,
                    sets = mutableListOf()
                )
            }.toMutableList()
            return WorkoutSession(
                template = template,
                exercises = exercises
            )
        }
    }
}

fun slugify(name: String): String {
    return name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

object WorkoutSeedData {
    val workouts: List<WorkoutTemplate> = listOf(
        WorkoutTemplate(
            name = "Push Day",
            restSeconds = 90,
            exercises = listOf(
                ExerciseTemplate("bench-press", "Bench Press", 4, 8, 135f),
                ExerciseTemplate("overhead-press", "Overhead Press", 3, 10, 95f),
                ExerciseTemplate("incline-dumbbell", "Incline Dumbbell", 3, 10, 50f),
                ExerciseTemplate("lateral-raise", "Lateral Raise", 3, 12, 15f),
                ExerciseTemplate("tricep-pushdown", "Tricep Pushdown", 3, 12, 40f)
            )
        ),
        WorkoutTemplate(
            name = "Pull Day",
            restSeconds = 90,
            exercises = listOf(
                ExerciseTemplate("deadlift", "Deadlift", 4, 6, 225f),
                ExerciseTemplate("pull-up", "Pull Up", 4, 8, 0f),
                ExerciseTemplate("barbell-row", "Barbell Row", 3, 10, 135f),
                ExerciseTemplate("face-pull", "Face Pull", 3, 12, 30f),
                ExerciseTemplate("bicep-curl", "Bicep Curl", 3, 12, 30f)
            )
        ),
        WorkoutTemplate(
            name = "Leg Day",
            restSeconds = 120,
            exercises = listOf(
                ExerciseTemplate("squat", "Squat", 4, 8, 185f),
                ExerciseTemplate("romanian-deadlift", "Romanian Deadlift", 3, 10, 135f),
                ExerciseTemplate("leg-press", "Leg Press", 3, 10, 200f),
                ExerciseTemplate("walking-lunge", "Walking Lunge", 3, 10, 0f),
                ExerciseTemplate("calf-raise", "Calf Raise", 4, 12, 100f)
            )
        ),
        WorkoutTemplate(
            name = "Full Body",
            restSeconds = 90,
            exercises = listOf(
                ExerciseTemplate("squat", "Squat", 3, 8, 185f),
                ExerciseTemplate("bench-press", "Bench Press", 3, 8, 135f),
                ExerciseTemplate("barbell-row", "Barbell Row", 3, 8, 135f),
                ExerciseTemplate("overhead-press", "Overhead Press", 3, 10, 95f)
            )
        ),
        WorkoutTemplate(
            name = "Upper",
            restSeconds = 120,
            exercises = listOf(
                ExerciseTemplate("shoulder-press", "Shoulder Press", 3, 8, 95f),
                ExerciseTemplate("seated-cable-row", "Seated Cable Row", 3, 8, 80f),
                ExerciseTemplate("chest-fly", "Chest Fly", 3, 8, 30f),
                ExerciseTemplate("face-pull", "Face Pull", 3, 8, 30f),
                ExerciseTemplate("triceps-pressdown", "Triceps Pressdown", 1, 8, 40f),
                ExerciseTemplate("preacher-curl", "Preacher Curl", 3, 8, 40f),
                ExerciseTemplate("lateral-raise", "Lateral Raise", 3, 8, 15f),
                ExerciseTemplate("incline-chest-press", "Incline Chest Press", 3, 8, 95f),
                ExerciseTemplate("cable-crunch", "Cable Crunch", 3, 15, 40f),
                ExerciseTemplate("single-arm-triceps-pushdown", "Single Arm Triceps Pushdown", 2, 8, 20f)
            )
        ),
        WorkoutTemplate(
            name = "Lower",
            restSeconds = 120,
            exercises = listOf(
                ExerciseTemplate("leg-press", "Leg Press", 3, 8, 200f),
                ExerciseTemplate("lying-leg-curl", "Lying Leg Curl", 3, 8, 60f),
                ExerciseTemplate("leg-extension", "Leg Extension", 3, 8, 70f),
                ExerciseTemplate("calf-press", "Calf Press", 3, 8, 100f),
                ExerciseTemplate("hip-abduction", "Hip Abduction", 3, 8, 55f),
                ExerciseTemplate("hip-adduction", "Hip Adduction", 3, 8, 55f)
            )
        )
    )
}
