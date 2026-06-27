package com.rokid.workouttracker

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

internal class ExerciseListScreenController(
    panelView: View
) : ViewScreenController(ScreenId.EXERCISE_LIST, panelView) {

    private val container = panelView as LinearLayout
    private var focusedRow = 0
    private var finishArmed = false
    private var abandonArmed = false

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
        finishArmed = false
        abandonArmed = false
    }

    override fun render() {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return
        val repo = activity.getRepository()

        container.removeAllViews()

        session.exercises.forEachIndexed { index, ep ->
            val isFocused = index == focusedRow

            val row = TextView(panelView.context).apply {
                val marker = if (isFocused) "> " else "  "
                val checkmarks = (0 until ep.totalSets).map { i ->
                    if (i < ep.doneSets) "✓" else "○"
                }.joinToString("")
                val vol = "${ep.doneSets}/${ep.totalSets}"
                val weightStr = if (ep.template.defaultWeight > 0) {
                    val w = ep.template.defaultWeight.toInt()
                    val u = if (ep.template.weightUnit == WeightUnit.KG) "kg" else "lb"
                    " $w$u"
                } else ""

                val prevStats = try {
                    val prev = repo.getPreviousSets(ep.template.id)
                    if (prev.isNotEmpty()) {
                        val last = prev.last()
                        "  Prev: ${last.weight.toInt()}x${last.reps}"
                    } else ""
                } catch (_: Exception) { "" }

                text = "$marker${ep.template.name}$weightStr  $checkmarks  $vol$prevStats"
                setTextColor(if (isFocused) fgColor else if (ep.isComplete) accentColor else mutedColor)
                setBackgroundColor(Color.TRANSPARENT)
                typeface = Typeface.create(Typeface.MONOSPACE, if (isFocused) Typeface.BOLD else Typeface.NORMAL)
                textSize = 14f
                setPadding(0, 6, 0, 2)
            }
            container.addView(row)
        }

        val finishRow = session.exercises.size

        val finish = TextView(panelView.context).apply {
            val isFinishFocused = focusedRow >= finishRow
            val marker = if (isFinishFocused) "> " else "  "
            val label: String
            val color: Int
            if (session.allExercisesComplete) {
                label = if (isFinishFocused) "> Finish Workout" else "  Finish Workout"
                color = if (isFinishFocused) accentColor else mutedColor
            } else if (isFinishFocused && finishArmed) {
                label = "> End Workout?"
                color = accentColor
            } else {
                label = if (isFinishFocused) "> End Workout" else "  End Workout"
                color = mutedColor
            }
            text = label
            setTextColor(color)
            setBackgroundColor(Color.TRANSPARENT)
            typeface = Typeface.create(Typeface.MONOSPACE, if (isFinishFocused) Typeface.BOLD else Typeface.NORMAL)
            textSize = 16f
            setPadding(0, 6, 0, 2)
        }
        container.addView(finish)
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return ScreenCommand.Open(ScreenId.MENU)

        val maxRow = session.exercises.size

        return when (action) {
            NavigationAction.SELECT -> {
                abandonArmed = false
                if (focusedRow < session.exercises.size) {
                    finishArmed = false
                    session.moveToExercise(focusedRow)
                    ScreenCommand.Open(ScreenId.EXERCISE)
                } else {
                    if (finishArmed || session.allExercisesComplete) {
                        finishArmed = false
                        ScreenCommand.FinishWorkout
                    } else {
                        finishArmed = true
                        ScreenCommand.Stay
                    }
                }
            }

            NavigationAction.BACK -> {
                finishArmed = false
                if (abandonArmed) {
                    ScreenCommand.AbandonWorkout
                } else {
                    abandonArmed = true
                    ScreenCommand.Stay
                }
            }

            NavigationAction.NEXT -> {
                finishArmed = false
                abandonArmed = false
                focusedRow = (focusedRow + 1).coerceAtMost(maxRow)
                ScreenCommand.Stay
            }

            NavigationAction.PREVIOUS -> {
                finishArmed = false
                abandonArmed = false
                focusedRow = (focusedRow - 1).coerceAtLeast(0)
                ScreenCommand.Stay
            }
        }
    }

    override fun navigationHint(context: Context): String {
        return when {
            abandonArmed -> context.getString(R.string.nav_exercise_list_abandon_confirm)
            finishArmed -> context.getString(R.string.nav_exercise_list_end_confirm)
            else -> context.getString(R.string.nav_exercise_list)
        }
    }
}
