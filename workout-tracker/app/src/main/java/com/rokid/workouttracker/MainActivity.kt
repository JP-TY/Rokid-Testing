package com.rokid.workouttracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBack()
        }
    }

    private val navigationInputMapper by lazy {
        NavigationInputMapper(
            context = this,
            onSelect = { handleAction(NavigationAction.SELECT) },
            onBack = { onBackPressedDispatcher.onBackPressed() },
            onNext = { handleAction(NavigationAction.NEXT) },
            onPrevious = { handleAction(NavigationAction.PREVIOUS) }
        )
    }

    private lateinit var headerTitleView: TextView
    private lateinit var headerTimeView: TextView
    private lateinit var footerNavigationView: TextView
    private lateinit var screenControllers: Map<ScreenId, ScreenController>

    private var currentScreen = ScreenId.MENU
    private var session: WorkoutSession? = null
    private var lastWorkoutDuration: Long = 0
    private var lastWorkoutResult: WorkoutResult? = null
    private var lastCompletedSession: WorkoutSession? = null

    private val workoutTimerHandler = Handler(Looper.getMainLooper())
    private var workoutTimerRunnable: Runnable? = null

    private lateinit var database: AppDatabase
    private lateinit var repository: WorkoutRepository
    private var customTemplates: List<WorkoutTemplate> = emptyList()

    private var idleHandler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var isSleeping = false
    private val IDLE_TIMEOUT_MS = 120_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)

        database = RoomBuilder.create(this)
        repository = WorkoutRepository(database)

        setContentView(R.layout.activity_main)
        bindViews()
        bindScreenControllers()

        refreshCustomWorkouts()
        restoreOrStart()
        currentScreenController().onEnter()
        renderUi()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()

        if (isSleeping && session != null) {
            isSleeping = false
            if (currentScreen == ScreenId.TIMER) {
                currentScreenController().onEnter()
            }
            renderUi()
        }

        resumeIdleTimer()
    }

    override fun onPause() {
        super.onPause()
        saveSessionIfActive()
        pauseIdleTimer()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        saveSessionIfActive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onDestroy() {
        stopWorkoutTimer()
        pauseIdleTimer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        resetIdleTimer()
        if (isSleeping) {
            wakeFromSleep()
            return true
        }
        return navigationInputMapper.onTouchEvent(event) || super.dispatchTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        resetIdleTimer()
        if (isSleeping) {
            wakeFromSleep()
            return true
        }
        return navigationInputMapper.onKeyUp(keyCode) || super.onKeyUp(keyCode, event)
    }

    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
    }

    private fun resumeIdleTimer() {
        if (session != null) {
            idleRunnable = Runnable { goToSleep() }
            idleHandler.postDelayed(idleRunnable!!, IDLE_TIMEOUT_MS)
        }
    }

    private fun pauseIdleTimer() {
        idleRunnable?.let { idleHandler.removeCallbacks(it) }
        idleRunnable = null
    }

    private fun resetIdleTimer() {
        if (idleRunnable != null) {
            idleHandler.removeCallbacks(idleRunnable!!)
            idleHandler.postDelayed(idleRunnable!!, IDLE_TIMEOUT_MS)
        }
    }

    private fun goToSleep() {
        isSleeping = true
        window.decorView.alpha = 0.2f
        saveSessionIfActive()
    }

    private fun wakeFromSleep() {
        isSleeping = false
        window.decorView.alpha = 1.0f
        applyImmersiveMode()
        if (currentScreen == ScreenId.TIMER) {
            currentScreenController().onEnter()
        }
        renderUi()
        resumeIdleTimer()
    }

    private fun saveSessionIfActive() {
        val s = session ?: return
        if (s.status == SessionStatus.ACTIVE) {
            repository.saveSession(s)
        }
    }

    private fun restoreOrStart() {
        val restored = repository.restoreSession()
        if (restored != null) {
            session = restored
            startWorkoutTimer()

            val exerciseIdx = restored.currentExerciseIndex
            if (exerciseIdx < restored.exercises.size && !restored.exercises[exerciseIdx].isComplete) {
                currentScreen = ScreenId.EXERCISE
            } else {
                currentScreen = ScreenId.EXERCISE_LIST
            }
        }
    }

    private fun handleBack() {
        handleAction(NavigationAction.BACK)
    }

    private fun bindViews() {
        headerTitleView = findViewById(R.id.headerTitleView)
        headerTimeView = findViewById(R.id.headerTimeView)
        footerNavigationView = findViewById(R.id.footerNavigationView)
    }

    private fun bindScreenControllers() {
        val menuController = MenuScreenController(findViewById(R.id.menuPanel))
        val historyController = HistoryScreenController(findViewById(R.id.historyPanel))
        val customWorkoutController = CustomWorkoutScreen(findViewById(R.id.customWorkoutPanel))
        val exerciseListController = ExerciseListScreenController(findViewById(R.id.exerciseListPanel))
        val exerciseController = ExerciseScreenController(findViewById(R.id.exercisePanel))
        val timerController = TimerScreenController(findViewById(R.id.timerPanel))
        val completeController = CompleteScreenController(findViewById(R.id.completePanel))
        screenControllers = mapOf(
            menuController.screen to menuController,
            historyController.screen to historyController,
            customWorkoutController.screen to customWorkoutController,
            exerciseListController.screen to exerciseListController,
            exerciseController.screen to exerciseController,
            timerController.screen to timerController,
            completeController.screen to completeController
        )
    }

    private fun handleAction(action: NavigationAction) {
        when (val result = currentScreenController().handleAction(action)) {
            ScreenCommand.Stay -> renderUi()
            ScreenCommand.ExitApp -> finish()
            ScreenCommand.FinishWorkout -> finishWorkout()
            ScreenCommand.AbandonWorkout -> abandonWorkout()
            ScreenCommand.SaveAndExit -> {
                saveSessionIfActive()
                navigateTo(ScreenId.MENU)
                renderUi()
            }
            is ScreenCommand.Open -> {
                navigateTo(result.screen)
                renderUi()
            }
        }
    }

    private fun navigateTo(screen: ScreenId) {
        if (currentScreen == screen) return
        currentScreenController().onExit()
        currentScreen = screen
        currentScreenController().onEnter()
    }

    private fun renderUi() {
        screenControllers.values.forEach { controller ->
            controller.setVisible(controller.screen == currentScreen)
            controller.render()
        }
        headerTitleView.text = when (currentScreen) {
            ScreenId.MENU -> getString(R.string.screen_menu_title)
            ScreenId.HISTORY -> getString(R.string.screen_history_title)
            ScreenId.CUSTOM_WORKOUT -> getString(R.string.screen_custom_workout_title)
            ScreenId.EXERCISE_LIST -> getString(R.string.screen_exercise_list_title)
            ScreenId.EXERCISE -> getString(R.string.screen_exercise_title)
            ScreenId.TIMER -> getString(R.string.screen_timer_title)
            ScreenId.COMPLETE -> getString(R.string.screen_complete_title)
        }
        headerTimeView.visibility = if (currentScreen == ScreenId.MENU || currentScreen == ScreenId.COMPLETE ||
            currentScreen == ScreenId.HISTORY || currentScreen == ScreenId.CUSTOM_WORKOUT
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }
        footerNavigationView.text = currentScreenController().navigationHint(this)
    }

    private fun currentScreenController(): ScreenController = screenControllers.getValue(currentScreen)

    // ── Public API for screen controllers ──

    fun getRepository(): WorkoutRepository = repository

    fun startWorkout(workoutIndex: Int) {
        val allTemplates = WorkoutSeedData.workouts + customTemplates
        if (workoutIndex >= allTemplates.size) return
        session = WorkoutSession.create(allTemplates[workoutIndex])
        startWorkoutTimer()
        resumeIdleTimer()
        navigateTo(ScreenId.EXERCISE_LIST)
        renderUi()
    }

    fun startCustomWorkout(template: WorkoutTemplate) {
        session = WorkoutSession.create(template)
        startWorkoutTimer()
        resumeIdleTimer()
        navigateTo(ScreenId.EXERCISE_LIST)
        renderUi()
    }

    fun getSession(): WorkoutSession? = session

    fun getLastWorkoutDuration(): Long = lastWorkoutDuration

    fun getLastWorkoutResult(): WorkoutResult? = lastWorkoutResult

    fun goToTimer() {
        navigateTo(ScreenId.TIMER)
        renderUi()
    }

    fun goToExerciseList() {
        navigateTo(ScreenId.EXERCISE_LIST)
        renderUi()
    }

    fun skipTimer() {
        val s = session
        if (s != null && s.currentExercise.isComplete) {
            navigateTo(ScreenId.EXERCISE_LIST)
        } else {
            navigateTo(ScreenId.EXERCISE)
        }
        renderUi()
    }

    fun finishWorkout() {
        stopWorkoutTimer()
        pauseIdleTimer()
        val s = session
        if (s != null) {
            lastWorkoutDuration = s.getElapsedSeconds()
            // Ensure session is saved to DB so it has an id before marking completed
            if (s.id == 0L) {
                s.status = SessionStatus.ACTIVE
                repository.saveSession(s)
            }
            s.status = SessionStatus.COMPLETED
            repository.updateSessionStatus(s, SessionStatus.COMPLETED)

            lastWorkoutResult = WorkoutResult(
                templateName = s.template.name,
                totalVolume = s.totalVolume(),
                weightUnit = s.exercises.firstOrNull()?.template?.weightUnit ?: WeightUnit.LB,
                totalSets = s.totalSetsLogged(),
                durationSeconds = lastWorkoutDuration
            )
            lastCompletedSession = s
        }
        session = null
        navigateTo(ScreenId.COMPLETE)
        renderUi()
    }

    fun abandonWorkout() {
        stopWorkoutTimer()
        pauseIdleTimer()
        val s = session
        if (s != null) {
            if (s.id == 0L) {
                s.status = SessionStatus.ACTIVE
                repository.saveSession(s)
            }
            s.status = SessionStatus.ABANDONED
            repository.updateSessionStatus(s, SessionStatus.ABANDONED)
        }
        session = null
        navigateTo(ScreenId.MENU)
        renderUi()
    }

    fun exportLastWorkout() {
        val s = lastCompletedSession
        val duration = lastWorkoutDuration
        if (s != null && duration > 0) {
            val ok = ExportWorkout.exportSession(this, s, duration)
            val msg = if (ok) getString(R.string.export_success) else getString(R.string.export_failed)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshCustomWorkouts() {
        customTemplates = repository.getCustomTemplates()
    }

    // ── Workout timer ──

    private fun startWorkoutTimer() {
        stopWorkoutTimer()
        updateTimerDisplay()
        workoutTimerRunnable = object : Runnable {
            override fun run() {
                updateTimerDisplay()
                workoutTimerHandler.postDelayed(this, 1000L)
            }
        }
        workoutTimerHandler.postDelayed(workoutTimerRunnable!!, 1000L)
    }

    private fun stopWorkoutTimer() {
        workoutTimerRunnable?.let { workoutTimerHandler.removeCallbacks(it) }
        workoutTimerRunnable = null
        headerTimeView.text = ""
    }

    private fun updateTimerDisplay() {
        headerTimeView.text = formatElapsed(session?.getElapsedSeconds() ?: 0)
    }

    private fun formatElapsed(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}
