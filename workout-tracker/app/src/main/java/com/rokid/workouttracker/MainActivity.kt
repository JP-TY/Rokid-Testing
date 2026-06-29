package com.rokid.workouttracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

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
    private lateinit var voiceStatusView: TextView
    private lateinit var screenControllers: Map<ScreenId, ScreenController>

    private var voiceRecognizer: VoiceCommandRecognizer? = null
    private var voiceStatusHandler = Handler(Looper.getMainLooper())
    private var voiceStatusClearRunnable: Runnable? = null
    private var dictationCallback: ((String, Boolean) -> Unit)? = null

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

    private lateinit var notificationManager: NotificationManager
    private var dndRestored = false

    private var idleHandler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var isSleeping = false
    private val IDLE_TIMEOUT_MS = 300_000L

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecognizer?.loadModel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)

        database = RoomBuilder.create(this)
        repository = WorkoutRepository(database)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        setContentView(R.layout.activity_main)
        bindViews()
        bindScreenControllers()

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                applyImmersiveMode()
            }
        }

        refreshCustomWorkouts()
        restoreOrStart()
        currentScreenController().onEnter()
        renderUi()

        initVoiceRecognizer()
    }

    private fun initVoiceRecognizer() {
        val recognizer = VoiceCommandRecognizer(
            context = this,
            grammarCommands = setOf(
                "select", "back", "next", "previous",
                "start", "stop", "finish", "save", "delete",
                "history", "menu", "export"
            ),
            onResult = { result ->
                runOnUiThread { handleVoiceResult(result) }
            }
        )
        voiceRecognizer = recognizer
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            recognizer.loadModel()
        } else {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleVoiceResult(result: VoiceCommandResult) {
        when (result) {
            is VoiceCommandResult.Recognized -> {
                showVoiceStatus("voice: ${result.command}")
                routeVoiceCommand(result.command)
            }
            is VoiceCommandResult.Dictation -> {
                if (dictationCallback != null) {
                    dictationCallback?.invoke(result.text, result.isFinal)
                    if (result.isFinal) {
                        dictationCallback = null
                    }
                } else if (result.text.isNotEmpty()) {
                    showVoiceStatus(result.text)
                }
            }
            VoiceCommandResult.ListeningStopped -> {
                if (dictationCallback != null) {
                    dictationCallback = null
                }
            }
            is VoiceCommandResult.AudioLevel -> {}
            is VoiceCommandResult.Error -> {
                Log.w("VoiceCommand", "Error: ${result.message}")
            }
            is VoiceCommandResult.ModelReady -> {
                Log.i("VoiceCommand", "Model ready")
                voiceRecognizer?.startListening()
            }
            VoiceCommandResult.ModelFailed -> {
                Log.w("VoiceCommand", "Model load failed")
            }
            VoiceCommandResult.ListeningStarted -> {}
        }
    }

    private fun routeVoiceCommand(command: String) {
        when (command) {
            "select", "ok", "go" -> handleAction(NavigationAction.SELECT)
            "back", "cancel", "exit" -> handleAction(NavigationAction.BACK)
            "next", "down", "right" -> handleAction(NavigationAction.NEXT)
            "previous", "up", "left" -> handleAction(NavigationAction.PREVIOUS)
            "start" -> {
                val s = session
                if (s != null && s.status == SessionStatus.ACTIVE) {
                    resumeSession()
                } else if (currentScreen == ScreenId.MENU) {
                    currentScreenController().handleAction(NavigationAction.SELECT)
                    renderUi()
                }
            }
            "finish", "stop" -> {
                if (currentScreen == ScreenId.EXERCISE_LIST) {
                    handleAction(NavigationAction.SELECT)
                }
            }
            "delete" -> {
                discardSession()
            }
            "save" -> {
                saveSessionIfActive()
                navigateTo(ScreenId.MENU)
                renderUi()
            }
            "history" -> {
                navigateTo(ScreenId.HISTORY)
                renderUi()
            }
            "menu", "home" -> {
                navigateTo(ScreenId.MENU)
                renderUi()
            }
            "export" -> {
                if (currentScreen == ScreenId.COMPLETE) {
                    lastCompletedSession?.let {
                        exportLastWorkout()
                    }
                }
            }
        }
    }

    private fun showVoiceStatus(text: String) {
        voiceStatusView.text = text
        voiceStatusView.visibility = View.VISIBLE
        voiceStatusClearRunnable?.let { voiceStatusHandler.removeCallbacks(it) }
        voiceStatusClearRunnable = Runnable {
            voiceStatusView.visibility = View.GONE
        }
        voiceStatusHandler.postDelayed(voiceStatusClearRunnable!!, 2000L)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        enableDnd()

        if (isSleeping && session != null) {
            isSleeping = false
            renderUi()
            resumeIdleTimer()
            return
        }

        if (session == null) {
            restoreOrStart()
            if (session != null) {
                startWorkoutTimer()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                resumeIdleTimer()
                navigateTo(currentScreen)
                renderUi()
                return
            }
        }

        resumeIdleTimer()
        voiceRecognizer?.let { v ->
            if (v.modelAvailable && v.hasRecordPermission) {
                v.startListening()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        voiceRecognizer?.stopListening()
        restoreDnd()
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
        voiceRecognizer?.destroy()
        voiceRecognizer = null
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

    @Suppress("DEPRECATION")
    @SuppressLint("GestureBackNavigation", "RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                resetIdleTimer()
                if (isSleeping) {
                    wakeFromSleep()
                    return true
                }
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
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
        window.decorView.windowInsetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun hasDndAccess(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun enableDnd() {
        if (!dndRestored && hasDndAccess()) {
            try {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                dndRestored = true
            } catch (_: SecurityException) {
                dndRestored = false
            }
        }
    }

    private fun restoreDnd() {
        if (dndRestored && hasDndAccess()) {
            try {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (_: SecurityException) {
                // ignore
            }
        }
        dndRestored = false
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
        currentScreenController().onEnter()
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
        voiceStatusView = findViewById(R.id.voiceStatusView)
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
            ScreenCommand.AbandonWorkout -> discardSession()
            ScreenCommand.DeleteSession -> discardSession()
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resumeIdleTimer()
        navigateTo(ScreenId.EXERCISE_LIST)
        renderUi()
    }

    fun startCustomWorkout(template: WorkoutTemplate) {
        session = WorkoutSession.create(template)
        startWorkoutTimer()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resumeIdleTimer()
        navigateTo(ScreenId.EXERCISE_LIST)
        renderUi()
    }

    fun getSession(): WorkoutSession? = session

    fun hasActiveSession(): Boolean {
        val s = session
        return s != null && s.status == SessionStatus.ACTIVE
    }

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

    fun resumeSession() {
        val s = session ?: return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startWorkoutTimer()
        resumeIdleTimer()
        navigateTo(ScreenId.EXERCISE_LIST)
        renderUi()
    }

    fun advanceFromRest() {
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
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val s = session
        if (s != null) {
            lastWorkoutDuration = s.getElapsedSeconds()
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

    fun discardSession() {
        stopWorkoutTimer()
        pauseIdleTimer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val s = session
        if (s != null) {
            if (s.id != 0L) {
                repository.clearActiveSession()
            }
        }
        session = null
        navigateTo(ScreenId.MENU)
        renderUi()
    }

    fun exportLastWorkout() {
        val s = lastCompletedSession
        val duration = lastWorkoutDuration
        if (s != null && duration > 0) {
            val fileName = ExportWorkout.exportSession(this, s, duration)
            if (fileName != null) {
                Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                android.util.Log.i("WorkoutExport", "Saved: $fileName")
            } else {
                Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshCustomWorkouts() {
        customTemplates = repository.getCustomTemplates()
    }

    fun enterDictationForName(callback: (String, Boolean) -> Unit) {
        voiceRecognizer?.switchToNameGrammar()
        dictationCallback = callback
    }

    fun exitDictation() {
        dictationCallback = null
        voiceRecognizer?.switchToCommandGrammar()
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
