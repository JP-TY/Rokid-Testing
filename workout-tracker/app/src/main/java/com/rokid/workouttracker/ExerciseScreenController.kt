package com.rokid.workouttracker

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView

internal class ExerciseScreenController(
    panelView: View
) : ViewScreenController(ScreenId.EXERCISE, panelView) {

    private val exerciseNameView: TextView = panelView.findViewById(R.id.exerciseNameView)
    private val exerciseProgressView: TextView = panelView.findViewById(R.id.exerciseProgressView)
    private val exercisePrevStatsView: TextView = panelView.findViewById(R.id.exercisePrevStatsView)
    private val exerciseWeightView: TextView = panelView.findViewById(R.id.exerciseWeightView)
    private val exerciseRepsView: TextView = panelView.findViewById(R.id.exerciseRepsView)
    private val logSetView: TextView = panelView.findViewById(R.id.logSetView)
    private val feedbackView: TextView = panelView.findViewById(R.id.exerciseFeedbackView)

    private var transitioning = false
    private var confirmArmed = false
    private var focusedRow = 0
    private val handler = Handler(Looper.getMainLooper())

    private val fgColor: Int
    private val mutedColor: Int
    private val accentColor: Int

    init {
        val ctx = panelView.context
        fgColor = ctx.getColor(R.color.hud_foreground)
        mutedColor = ctx.getColor(R.color.hud_foreground_muted)
        accentColor = ctx.getColor(R.color.hud_accent)
    }

    override fun onEnter() {
        transitioning = false
        confirmArmed = false
        focusedRow = 0
        feedbackView.visibility = View.GONE
        logSetView.visibility = View.VISIBLE
    }

    override fun onExit() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun render() {
        if (transitioning) return
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return
        val exercise = session.currentExercise
        val setNumber = exercise.doneSets + 1

        exerciseNameView.text = exercise.template.name
        exerciseProgressView.text = panelView.context.getString(
            R.string.exercise_set_progress, setNumber, exercise.totalSets, exercise.template.targetReps
        )

        val weightUnitLabel = if (exercise.template.weightUnit == WeightUnit.KG) "kg" else "lb"
        val weightWhole = session.currentWeightAdjustment.toInt()

        exerciseWeightView.text = panelView.context.getString(R.string.exercise_weight_label, weightWhole, weightUnitLabel)
        exerciseRepsView.text = panelView.context.getString(R.string.exercise_reps_label, session.currentRepAdjustment)

        val repo = activity.getRepository()
        val prevSets = repo.getPreviousSets(exercise.template.id)
        if (prevSets.isNotEmpty()) {
            val last = prevSets.last()
            val pu = if (last.weightUnit == WeightUnit.KG) "kg" else "lb"
            val pw = last.weight.toInt()
            exercisePrevStatsView.text = panelView.context.getString(R.string.exercise_prev_stats_format, pw, pu, last.reps)
            exercisePrevStatsView.visibility = View.VISIBLE
        } else {
            exercisePrevStatsView.visibility = View.GONE
        }

        when (focusedRow) {
            0 -> {
                exerciseWeightView.setTextColor(fgColor)
                exerciseWeightView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                exerciseRepsView.setTextColor(mutedColor)
                exerciseRepsView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                logSetView.setTextColor(mutedColor)
                logSetView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                logSetView.text = panelView.context.getString(R.string.exercise_log_button)
            }
            1 -> {
                exerciseWeightView.setTextColor(mutedColor)
                exerciseWeightView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                exerciseRepsView.setTextColor(fgColor)
                exerciseRepsView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                logSetView.setTextColor(mutedColor)
                logSetView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                logSetView.text = panelView.context.getString(R.string.exercise_log_button)
            }
            2 -> {
                exerciseWeightView.setTextColor(mutedColor)
                exerciseWeightView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                exerciseRepsView.setTextColor(mutedColor)
                exerciseRepsView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                logSetView.setTextColor(if (confirmArmed) accentColor else fgColor)
                logSetView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                logSetView.text = if (confirmArmed) {
                    panelView.context.getString(R.string.exercise_confirm_button)
                } else {
                    panelView.context.getString(R.string.exercise_log_button)
                }
            }
        }
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return ScreenCommand.Open(ScreenId.MENU)

        if (transitioning) {
            if (action == NavigationAction.BACK) {
                handler.removeCallbacksAndMessages(null)
                transitioning = false
                return ScreenCommand.Open(ScreenId.EXERCISE_LIST)
            }
            return ScreenCommand.Stay
        }

        return when (action) {
            NavigationAction.SELECT -> {
                when (focusedRow) {
                    2 -> {
                        if (confirmArmed) {
                            confirmArmed = false
                            session.logCurrentSet()
                            val logged = session.lastLoggedSet!!
                            val reps = logged.reps
                            val w = logged.weight.toInt()
                            val unit = if (logged.weightUnit == WeightUnit.KG) "kg" else "lb"
                            transitioning = true

                            logSetView.visibility = View.GONE
                            feedbackView.visibility = View.VISIBLE
                            feedbackView.text = panelView.context.getString(R.string.exercise_logged_feedback, w, unit, reps)

                            handler.postDelayed({
                                transitioning = false
                                activity.goToTimer()
                            }, 800L)
                        } else {
                            confirmArmed = true
                        }
                        ScreenCommand.Stay
                    }
                    else -> {
                        confirmArmed = false
                        focusedRow = (focusedRow + 1) % 3
                        ScreenCommand.Stay
                    }
                }
            }

            NavigationAction.BACK -> {
                when {
                    focusedRow == 2 && confirmArmed -> {
                        confirmArmed = false
                        ScreenCommand.Stay
                    }
                    focusedRow == 2 -> {
                        focusedRow = 1
                        ScreenCommand.Stay
                    }
                    focusedRow == 1 -> {
                        focusedRow = 0
                        ScreenCommand.Stay
                    }
                    else -> {
                        confirmArmed = false
                        ScreenCommand.Open(ScreenId.EXERCISE_LIST)
                    }
                }
            }

            NavigationAction.NEXT -> {
                confirmArmed = false
                when (focusedRow) {
                    0 -> adjustWeightUp(session)
                    1 -> session.currentRepAdjustment++
                    2 -> {}
                }
                ScreenCommand.Stay
            }

            NavigationAction.PREVIOUS -> {
                confirmArmed = false
                when (focusedRow) {
                    0 -> adjustWeightDown(session)
                    1 -> session.currentRepAdjustment = (session.currentRepAdjustment - 1).coerceAtLeast(0)
                    2 -> {}
                }
                ScreenCommand.Stay
            }
        }
    }

    private fun adjustWeightUp(session: WorkoutSession) {
        val inc = if (session.currentExercise.template.weightUnit == WeightUnit.KG) 2.5f else 5f
        session.currentWeightAdjustment += inc
    }

    private fun adjustWeightDown(session: WorkoutSession) {
        val dec = if (session.currentExercise.template.weightUnit == WeightUnit.KG) 2.5f else 5f
        session.currentWeightAdjustment = (session.currentWeightAdjustment - dec).coerceAtLeast(0f)
    }

    override fun navigationHint(context: Context): String {
        return if (confirmArmed) {
            context.getString(R.string.nav_exercise_confirm)
        } else {
            context.getString(R.string.nav_exercise)
        }
    }
}
