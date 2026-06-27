package com.rokid.workouttracker

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateName: String,
    val status: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val exercisesJson: String,
    val durationSeconds: Long = 0,
    val totalVolume: Float = 0f,
    val totalSets: Int = 0
)

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startTimeMillis DESC")
    fun getAll(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun getById(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startTimeMillis DESC LIMIT 1")
    fun getLastActive(): WorkoutSessionEntity?

    @Insert
    fun insert(session: WorkoutSessionEntity): Long

    @Query("UPDATE workout_sessions SET status = :status, endTimeMillis = :endTime, durationSeconds = :duration, totalVolume = :volume, totalSets = :sets WHERE id = :id")
    fun finish(id: Long, status: String, endTime: Long, duration: Long, volume: Float, sets: Int)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    fun delete(id: Long)

    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY startTimeMillis DESC LIMIT 3")
    fun getLastThreeCompleted(): List<WorkoutSessionEntity>
}

@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM custom_templates ORDER BY id DESC")
    fun getAll(): List<CustomTemplateEntity>

    @Insert
    fun insert(template: CustomTemplateEntity): Long

    @Query("DELETE FROM custom_templates WHERE id = :id")
    fun delete(id: Long)
}

@Entity(tableName = "custom_templates")
data class CustomTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val restSeconds: Int,
    val exercisesJson: String
)

@Database(entities = [WorkoutSessionEntity::class, CustomTemplateEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
}

class WorkoutRepository(private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    private val sessionDao = db.workoutSessionDao()
    private val templateDao = db.workoutTemplateDao()

    fun saveSession(session: WorkoutSession) {
        val existing = sessionDao.getById(session.id)
        if (existing != null) {
            val elapsed = session.getElapsedSeconds()
            sessionDao.finish(
                id = session.id,
                status = session.status.name,
                endTime = System.currentTimeMillis(),
                duration = elapsed,
                volume = session.totalVolume(),
                sets = session.totalSetsLogged()
            )
        } else {
            val data = WorkoutSessionData(
                currentExerciseIndex = session.currentExerciseIndex,
                currentRepAdjustment = session.currentRepAdjustment,
                currentWeightAdjustment = session.currentWeightAdjustment,
                exercises = session.exercises.map { ep ->
                    ExerciseData(
                        templateId = ep.template.id,
                        sets = ep.sets.map { s ->
                            SetData(s.reps, s.weight, s.weightUnit.name, s.timestamp)
                        }
                    )
                }
            )
            val entity = WorkoutSessionEntity(
                templateName = session.template.name,
                status = session.status.name,
                startTimeMillis = session.workoutStartTime,
                exercisesJson = json.encodeToString(data)
            )
            session.id = sessionDao.insert(entity)
        }
    }

    fun updateSessionStatus(session: WorkoutSession, status: SessionStatus) {
        if (session.id == 0L) return
        val elapsed = session.getElapsedSeconds()
        sessionDao.finish(
            id = session.id,
            status = status.name,
            endTime = System.currentTimeMillis(),
            duration = elapsed,
            volume = session.totalVolume(),
            sets = session.totalSetsLogged()
        )
        session.status = status
    }

    fun restoreSession(): WorkoutSession? {
        val active = sessionDao.getLastActive() ?: return null
        val data: WorkoutSessionData
        try {
            data = json.decodeFromString(active.exercisesJson)
        } catch (_: Exception) {
            sessionDao.delete(active.id)
            return null
        }
        val template = WorkoutSeedData.workouts.find { it.name == active.templateName }
            ?: getCustomTemplates().find { it.name == active.templateName }
            ?: return null
        val exercises = template.exercises.map { template ->
            val exData = data.exercises.find { it.templateId == template.id }
            ExerciseProgress(
                template = template,
                sets = (exData?.sets?.map { LoggedSet(it.reps, it.weight, WeightUnit.valueOf(it.weightUnit), it.timestamp) }
                    ?: emptyList()).toMutableList()
            )
        }.toMutableList()
        val session = WorkoutSession(
            template = template,
            exercises = exercises,
            workoutStartTime = active.startTimeMillis,
            id = active.id
        )
        session.currentExerciseIndex = data.currentExerciseIndex
        session.currentRepAdjustment = data.currentRepAdjustment
        session.currentWeightAdjustment = data.currentWeightAdjustment
        session.status = SessionStatus.ACTIVE
        return session
    }

    fun clearActiveSession() {
        val active = sessionDao.getLastActive() ?: return
        sessionDao.delete(active.id)
    }

    fun getCompletedSessions(): List<WorkoutSessionEntity> {
        return sessionDao.getAll().filter { it.status == "COMPLETED" }
    }

    fun getPreviousSets(exerciseId: String): List<LoggedSet> {
        val completed = sessionDao.getLastThreeCompleted()
        val allSets = mutableListOf<LoggedSet>()
        for (session in completed) {
            try {
                val data: WorkoutSessionData = json.decodeFromString(session.exercisesJson)
                val exData = data.exercises.find { it.templateId == exerciseId } ?: continue
                allSets.addAll(exData.sets.map { LoggedSet(it.reps, it.weight, WeightUnit.valueOf(it.weightUnit), it.timestamp) })
            } catch (_: Exception) { }
        }
        return allSets
    }

    fun saveCustomTemplate(template: WorkoutTemplate): Long {
        val data = CustomTemplateExercises(
            exercises = template.exercises.map { CustomExerciseData(it.id, it.name, it.sets, it.targetReps, it.defaultWeight, it.weightUnit.name, it.restSeconds) }
        )
        val entity = CustomTemplateEntity(
            name = template.name,
            restSeconds = template.restSeconds,
            exercisesJson = json.encodeToString(data)
        )
        return templateDao.insert(entity)
    }

    fun getCustomTemplates(): List<WorkoutTemplate> {
        return templateDao.getAll().mapNotNull { entity ->
            try {
                val data: CustomTemplateExercises = json.decodeFromString(entity.exercisesJson)
                WorkoutTemplate(
                    name = entity.name,
                    restSeconds = entity.restSeconds,
                    exercises = data.exercises.map { ex ->
                        ExerciseTemplate(ex.id, ex.name, ex.sets, ex.targetReps, ex.defaultWeight, WeightUnit.valueOf(ex.weightUnit), ex.restSeconds)
                    }
                )
            } catch (_: Exception) { null }
        }
    }

    fun deleteCustomTemplate(name: String) {
        val all = templateDao.getAll()
        all.find { it.name == name }?.let { templateDao.delete(it.id) }
    }
}

@Serializable
data class WorkoutSessionData(
    val currentExerciseIndex: Int = 0,
    val currentRepAdjustment: Int = 0,
    val currentWeightAdjustment: Float = 0f,
    val exercises: List<ExerciseData> = emptyList()
)

@Serializable
data class ExerciseData(
    val templateId: String,
    val sets: List<SetData> = emptyList()
)

@Serializable
data class SetData(
    val reps: Int,
    val weight: Float = 0f,
    val weightUnit: String = "KG",
    val timestamp: Long = 0L
)
@Serializable
data class CustomTemplateExercises(
    val exercises: List<CustomExerciseData> = emptyList()
)

@Serializable
data class CustomExerciseData(
    val id: String,
    val name: String,
    val sets: Int,
    val targetReps: Int,
    val defaultWeight: Float = 0f,
    val weightUnit: String = "KG",
    val restSeconds: Int? = null
)
