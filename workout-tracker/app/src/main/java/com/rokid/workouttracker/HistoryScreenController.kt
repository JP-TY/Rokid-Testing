package com.rokid.workouttracker

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class HistoryScreenController(
    panelView: View
) : ViewScreenController(ScreenId.HISTORY, panelView) {

    private val emptyView: TextView = panelView.findViewById(R.id.historyEmptyView)
    private val container: LinearLayout = panelView.findViewById(R.id.historyContainer)

    private var focusedIndex = 0
    private var sessions: List<WorkoutSessionEntity> = emptyList()
    private var detailSessionIdx = -1
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val visibleRows = 6

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
        focusedIndex = 0
        detailSessionIdx = -1
        val activity = panelView.context as MainActivity
        sessions = activity.getRepository().getCompletedSessions()
    }

    override fun render() {
        container.removeAllViews()
        if (sessions.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        if (detailSessionIdx >= 0 && detailSessionIdx < sessions.size) {
            renderDetail(sessions[detailSessionIdx])
        } else {
            renderList()
        }
    }

    private fun renderList() {
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val total = sessions.size
        val windowStart = (focusedIndex - visibleRows / 2).coerceAtLeast(0)
            .coerceAtMost((total - visibleRows).coerceAtLeast(0))
        val windowEnd = (windowStart + visibleRows).coerceAtMost(total)

        for (index in windowStart until windowEnd) {
            val session = sessions[index]
            val isFocused = index == focusedIndex
            val marker = if (isFocused) "> " else "  "
            val dateStr = dateFormat.format(Date(session.startTimeMillis))
            val durMinutes = session.durationSeconds / 60
            val setsStr = panelView.context.getString(R.string.complete_sets, session.totalSets)
            val volStr = if (session.totalVolume > 0) {
                "  Vol: ${session.totalVolume.toInt()}"
            } else ""

            val row = TextView(panelView.context).apply {
                text = panelView.context.getString(R.string.history_row, marker, session.templateName, dateStr, durMinutes, setsStr, volStr)
                setTextColor(if (isFocused) fgColor else mutedColor)
                typeface = Typeface.create(Typeface.MONOSPACE, if (isFocused) Typeface.BOLD else Typeface.NORMAL)
                textSize = 12f
                setPadding(0, 8, 0, 4)
            }
            container.addView(row)
        }
    }

    private fun renderDetail(session: WorkoutSessionEntity) {
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(session.startTimeMillis))
        val durMinutes = session.durationSeconds / 60

        val header = TextView(panelView.context).apply {
            text = panelView.context.getString(R.string.history_detail_header, session.templateName, dateStr, durMinutes)
            setTextColor(fgColor)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 13f
            setPadding(0, 4, 0, 8)
        }
        container.addView(header)

        try {
            val data: WorkoutSessionData = json.decodeFromString(session.exercisesJson)
            for ((ei, exercise) in data.exercises.withIndex()) {
                val exName = unslugify(exercise.templateId)
                val exLabel = TextView(panelView.context).apply {
                    text = panelView.context.getString(R.string.history_exercise_num, ei + 1, exName)
                    setTextColor(accentColor)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = 12f
                    setPadding(0, 6, 0, 2)
                }
                container.addView(exLabel)

                for ((si, set) in exercise.sets.withIndex()) {
                    val unit = if (set.weightUnit == "KG") "kg" else "lb"
                    val w = set.weight.toInt()
                    val setText = TextView(panelView.context).apply {
                        text = panelView.context.getString(R.string.history_set_detail, si + 1, w, unit, set.reps)
                        setTextColor(mutedColor)
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                        textSize = 11f
                        setPadding(0, 2, 0, 2)
                    }
                    container.addView(setText)
                }
            }
        } catch (_: Exception) {
            val err = TextView(panelView.context).apply {
                text = panelView.context.getString(R.string.history_detail_load_error)
                setTextColor(mutedColor)
                textSize = 12f
            }
            container.addView(err)
        }
    }

    private fun unslugify(slug: String): String {
        return slug.split("-").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        if (detailSessionIdx >= 0) {
            return handleDetailAction(action)
        }
        return handleListAction(action)
    }

    private fun handleListAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                if (sessions.isEmpty()) return ScreenCommand.Stay
                detailSessionIdx = focusedIndex
                ScreenCommand.Stay
            }
            NavigationAction.BACK -> ScreenCommand.Open(ScreenId.MENU)
            NavigationAction.NEXT -> {
                if (sessions.isNotEmpty()) focusedIndex = (focusedIndex + 1).coerceAtMost(sessions.lastIndex)
                ScreenCommand.Stay
            }
            NavigationAction.PREVIOUS -> {
                focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                ScreenCommand.Stay
            }
        }
    }

    private fun handleDetailAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.BACK -> {
                detailSessionIdx = -1
                ScreenCommand.Stay
            }
            else -> ScreenCommand.Stay
        }
    }

    override fun navigationHint(context: Context): String {
        return if (detailSessionIdx >= 0) {
            panelView.context.getString(R.string.history_detail_back)
        } else {
            panelView.context.getString(R.string.nav_history)
        }
    }
}
