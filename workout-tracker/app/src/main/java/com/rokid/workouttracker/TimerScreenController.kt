package com.rokid.workouttracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView

internal class TimerScreenController(
    panelView: View
) : ViewScreenController(ScreenId.TIMER, panelView) {

    private val setsRemainingView: TextView = panelView.findViewById(R.id.timerSetsRemainingView)
    private val countdownView: TextView = panelView.findViewById(R.id.timerCountdownView)

    private var countdownSeconds = 0
    private var countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var hasSkipped = false

    override fun onEnter() {
        hasSkipped = false
        val activity = panelView.context as MainActivity
        val session = activity.getSession()
        if (session != null) {
            val ex = session.currentExercise
            countdownSeconds = ex.template.restSeconds ?: session.template.restSeconds
            updateDisplay()
            startCountdown()
        }
    }

    override fun render() {
        updateDisplay()
    }

    override fun onExit() {
        stopCountdown()
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        val activity = panelView.context as MainActivity
        return when (action) {
            NavigationAction.SELECT, NavigationAction.BACK -> {
                if (!hasSkipped) {
                    hasSkipped = true
                    stopCountdown()
                    activity.advanceFromRest()
                }
                ScreenCommand.Stay
            }
            else -> ScreenCommand.Stay
        }
    }

    override fun navigationHint(context: Context): String {
        return if (countdownSeconds > 0) {
            context.getString(R.string.nav_timer, countdownSeconds)
        } else {
            panelView.context.getString(R.string.nav_timer_no_countdown)
        }
    }

    private fun startCountdown() {
        stopCountdown()
        countdownRunnable = object : Runnable {
            override fun run() {
                if (hasSkipped) return
                countdownSeconds--
                if (countdownSeconds <= 0) {
                    countdownView.text = "0"
                    hasSkipped = true
                    val activity = panelView.context as MainActivity
                    activity.advanceFromRest()
                } else {
                    updateDisplay()
                    countdownHandler.postDelayed(this, 1000L)
                }
            }
        }
        countdownHandler.postDelayed(countdownRunnable!!, 1000L)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun updateDisplay() {
        val activity = panelView.context as MainActivity
        val session = activity.getSession()
        if (session != null) {
            val ex = session.currentExercise
            if (ex.isComplete) {
                setsRemainingView.text = panelView.context.getString(R.string.timer_all_sets_complete)
                countdownView.text = ""
            } else {
                val setNum = ex.doneSets + 1
                setsRemainingView.text = panelView.context.getString(R.string.timer_sets_remaining, setNum, ex.totalSets)
                countdownView.text = countdownSeconds.toString()
            }
        }
    }
}
