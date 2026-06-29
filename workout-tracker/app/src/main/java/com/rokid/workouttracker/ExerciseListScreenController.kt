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
    private var deleteArmed = false

    private val fgColor: Int
    private val mutedColor: Int
    private val accentColor: Int
    private val visibleRows = 6

    private val rowViews = mutableListOf<View>()

    init {
        val ctx = panelView.context
        fgColor = ctx.getColor(R.color.hud_foreground)
        mutedColor = ctx.getColor(R.color.hud_foreground_muted)
        accentColor = ctx.getColor(R.color.hud_accent)
    }

    override fun onEnter() {
        finishArmed = false
        deleteArmed = false
        focusedRow = 0
        buildRows()
    }

    private fun buildRows() {
        rowViews.clear()
        container.removeAllViews()

        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return
        val repo = activity.getRepository()

        session.exercises.forEachIndexed { index, ep ->
            val row = TextView(panelView.context).apply {
                textSize = 14f
                setPadding(0, 6, 0, 2)
                typeface = Typeface.MONOSPACE
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowViews.add(row)
            container.addView(row)
        }

        val sep = View(panelView.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 8, 0, 8) }
            setBackgroundColor(mutedColor)
        }
        rowViews.add(sep)
        container.addView(sep)

        val finishRow = createActionRow()
        rowViews.add(finishRow)
        container.addView(finishRow)

        val deleteRow = createActionRow()
        rowViews.add(deleteRow)
        container.addView(deleteRow)

        render()
    }

    private fun createActionRow(): TextView {
        return TextView(panelView.context).apply {
            textSize = 16f
            setPadding(0, 6, 0, 2)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun render() {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return
        val repo = activity.getRepository()

        val exerciseCount = session.exercises.size
        val totalRows = exerciseCount + 3
        val rowCount = rowViews.size

        val validIndices = (0 until exerciseCount).toList()
        val currentPos = validIndices.indexOf(focusedRow)

        val windowStart = if (currentPos < 0) 0 else {
            (currentPos - (visibleRows / 2)).coerceAtLeast(0)
                .coerceAtMost((exerciseCount - visibleRows).coerceAtLeast(0))
        }
        val windowEnd = (windowStart + visibleRows).coerceAtMost(exerciseCount)

        session.exercises.forEachIndexed { index, ep ->
            if (index >= rowCount) return@forEachIndexed
            val row = rowViews[index] as? TextView ?: return@forEachIndexed
            val isFocused = index == focusedRow

            val posInValid = validIndices.indexOf(index)
            if (posInValid < windowStart || posInValid >= windowEnd) {
                row.visibility = View.GONE
                return@forEachIndexed
            }
            row.visibility = View.VISIBLE

            val marker = if (isFocused) "> " else "  "
            val checkmarks = (0 until ep.totalSets).map { i ->
                if (i < ep.doneSets) "\u2713" else "\u25CB"
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
                    "  ${panelView.context.getString(R.string.exercise_list_prev_stats, last.weight.toInt(), last.reps)}"
                } else ""
            } catch (_: Exception) { "" }

            row.text = panelView.context.getString(R.string.exercise_list_row, marker, ep.template.name, weightStr, checkmarks, vol, prevStats)
            row.setTextColor(if (isFocused) fgColor else if (ep.isComplete) accentColor else mutedColor)
            row.typeface = Typeface.create(Typeface.MONOSPACE, if (isFocused) Typeface.BOLD else Typeface.NORMAL)
        }

        val sepIndex = exerciseCount
        if (sepIndex < rowCount) {
            rowViews[sepIndex].visibility = if (exerciseCount > 0) View.VISIBLE else View.GONE
        }

        val finishIndex = exerciseCount + 1
        if (finishIndex < rowCount) {
            val tv = rowViews[finishIndex] as? TextView
            if (tv != null) {
                val isFocused = focusedRow == finishIndex
                val label = when {
                    isFocused && finishArmed -> panelView.context.getString(R.string.exercise_list_finish_prompt)
                    isFocused -> panelView.context.getString(R.string.exercise_list_finish)
                    else -> "  ${panelView.context.getString(R.string.exercise_list_finish)}"
                }
                tv.text = label
                tv.setTextColor(if (isFocused) accentColor else mutedColor)
                tv.typeface = Typeface.create(Typeface.MONOSPACE, if (isFocused) Typeface.BOLD else Typeface.NORMAL)
                tv.visibility = View.VISIBLE
            }
        }

        val deleteIndex = exerciseCount + 2
        if (deleteIndex < rowCount) {
            val tv = rowViews[deleteIndex] as? TextView
            if (tv != null) {
                val isFocused = focusedRow == deleteIndex
                val label = when {
                    isFocused && deleteArmed -> panelView.context.getString(R.string.exercise_list_delete_prompt)
                    isFocused -> panelView.context.getString(R.string.exercise_list_delete)
                    else -> "  ${panelView.context.getString(R.string.exercise_list_delete)}"
                }
                tv.text = label
                tv.setTextColor(if (isFocused) accentColor else mutedColor)
                tv.typeface = Typeface.create(Typeface.MONOSPACE, if (isFocused) Typeface.BOLD else Typeface.NORMAL)
                tv.visibility = View.VISIBLE
            }
        }
    }

    private fun navigateRow(delta: Int) {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return
        val exerciseCount = session.exercises.size
        val sepIndex = exerciseCount
        val maxRow = exerciseCount + 2
        var target = (focusedRow + delta).coerceIn(0, maxRow)
        // Skip the separator row
        if (target == sepIndex) {
            target = if (delta > 0) target + 1 else target - 1
            target = target.coerceIn(0, maxRow)
        }
        focusedRow = target
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        val activity = panelView.context as MainActivity
        val session = activity.getSession() ?: return ScreenCommand.Open(ScreenId.MENU)

        val exerciseCount = session.exercises.size
        val finishIndex = exerciseCount + 1
        val deleteIndex = exerciseCount + 2

        return when (action) {
            NavigationAction.SELECT -> {
                if (focusedRow < exerciseCount) {
                    finishArmed = false
                    deleteArmed = false
                    session.moveToExercise(focusedRow)
                    ScreenCommand.Open(ScreenId.EXERCISE)
                } else if (focusedRow == finishIndex) {
                    deleteArmed = false
                    if (finishArmed || session.allExercisesComplete) {
                        finishArmed = false
                        ScreenCommand.FinishWorkout
                    } else {
                        finishArmed = true
                        ScreenCommand.Stay
                    }
                } else if (focusedRow == deleteIndex) {
                    finishArmed = false
                    if (deleteArmed) {
                        deleteArmed = false
                        ScreenCommand.DeleteSession
                    } else {
                        deleteArmed = true
                        ScreenCommand.Stay
                    }
                } else {
                    finishArmed = false
                    deleteArmed = false
                    ScreenCommand.Stay
                }
            }

            NavigationAction.BACK -> {
                finishArmed = false
                deleteArmed = false
                ScreenCommand.SaveAndExit
            }

            NavigationAction.NEXT -> {
                finishArmed = false
                deleteArmed = false
                navigateRow(+1)
                ScreenCommand.Stay
            }

            NavigationAction.PREVIOUS -> {
                finishArmed = false
                deleteArmed = false
                navigateRow(-1)
                ScreenCommand.Stay
            }
        }
    }

    override fun navigationHint(context: Context): String {
        return when {
            deleteArmed -> context.getString(R.string.nav_exercise_list_delete_confirm)
            finishArmed -> context.getString(R.string.nav_exercise_list_end_confirm)
            else -> context.getString(R.string.nav_exercise_list)
        }
    }
}
