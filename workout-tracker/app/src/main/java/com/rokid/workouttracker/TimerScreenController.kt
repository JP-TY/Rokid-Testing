package com.rokid.workouttracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView

internal class TimerScreenController(
    panelView: View
) : ViewScreenController(ScreenId.TIMER, panelView) {

    private val timerCountdownView: TextView = panelView.findViewById(R.id.timerCountdownView)
    private val timerProgressContainer: View = panelView.findViewById(R.id.timerProgressContainer)
    private val timerProgressFill: View = panelView.findViewById(R.id.timerProgressFill)
    private val timerExerciseInfo: TextView = panelView.findViewById(R.id.timerExerciseInfo)

    private var secondsRemaining = 0
    private var totalSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (secondsRemaining <= 0) {
                onTimerComplete()
                return
            }
            secondsRemaining--
            updateDisplay()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onEnter() {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return
        val exercise = session.currentExercise.template

        secondsRemaining = exercise.restSeconds ?: session.template.restSeconds
        totalSeconds = secondsRemaining
        updateDisplay()
        handler.postDelayed(tickRunnable, 1000L)
    }

    override fun render() {
        updateDisplay()
    }

    override fun onExit() {
        handler.removeCallbacks(tickRunnable)
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        val activity = panelView.context as MainActivity
        return when (action) {
            NavigationAction.SELECT, NavigationAction.BACK -> {
                handler.removeCallbacks(tickRunnable)
                activity.skipTimer()
                ScreenCommand.Stay
            }

            else -> ScreenCommand.Stay
        }
    }

    override fun navigationHint(context: Context): String {
        return context.getString(R.string.nav_timer)
    }

    private fun updateDisplay() {
        val minutes = secondsRemaining / 60
        val seconds = secondsRemaining % 60
        timerCountdownView.text = String.format("%d:%02d", minutes, seconds)

        val fillWidth = if (totalSeconds > 0) {
            timerProgressContainer.width * secondsRemaining / totalSeconds
        } else 0
        timerProgressFill.layoutParams.width = fillWidth
        timerProgressFill.requestLayout()

        val activity = panelView.context as MainActivity
        val session = activity.getSession()
        if (session != null) {
            val ex = session.currentExercise
            if (ex.isComplete) {
                timerExerciseInfo.text = "Next: Exercise List"
            } else {
                val setNum = ex.doneSets + 1
                timerExerciseInfo.text = "Next: ${ex.template.name}  Set $setNum/${ex.sets}"
            }
        }
    }

    private fun onTimerComplete() {
        val activity = panelView.context as MainActivity
        activity.skipTimer()
    }
}
