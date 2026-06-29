package com.rokid.workouttracker

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

internal class MenuScreenController(
    panelView: View
) : ViewScreenController(ScreenId.MENU, panelView) {

    private val seedLabels = listOf(
        R.string.menu_workout_push, R.string.menu_workout_pull,
        R.string.menu_workout_legs, R.string.menu_workout_fullbody,
        R.string.menu_workout_upper, R.string.menu_workout_lower
    )

    private data class MenuRow(
        val label: String,
        val isCustom: Boolean = false,
        val isActive: Boolean = false,
        val isDeletable: Boolean = false,
        val textView: TextView? = null
    )

    private val menuRows = mutableListOf<MenuRow>()
    private var focusedIndex = 0
    private var quitConfirmationArmed = false
    private var deleteArmed = false
    private val container: LinearLayout = panelView.findViewById(R.id.menuContainer)
    private var customTemplates: List<WorkoutTemplate> = emptyList()
    private var activeSessionName: String? = null
    private val visibleRows = 6

    override fun onEnter() {
        quitConfirmationArmed = false
        deleteArmed = false
        focusedIndex = 0
        val activity = panelView.context as MainActivity
        customTemplates = activity.getRepository().getCustomTemplates()
        activeSessionName = if (activity.hasActiveSession()) {
            activity.getSession()?.template?.name
        } else null
        buildMenu()
    }

    private fun buildMenu() {
        menuRows.clear()
        container.removeAllViews()

        if (activeSessionName != null) {
            val label = "\u25B6 ${activeSessionName} (In Progress)"
            menuRows.add(MenuRow(label, isActive = true))
            val tv = createRow(label, isSpecial = true)
            container.addView(tv)
            menuRows[menuRows.lastIndex] = menuRows.last().copy(textView = tv)
        }

        seedLabels.forEachIndexed { i, labelRes ->
            val label = panelView.context.getString(labelRes)
            menuRows.add(MenuRow(label))
            val tv = createRow(label, isSpecial = false)
            container.addView(tv)
            menuRows[menuRows.lastIndex] = menuRows.last().copy(textView = tv)
        }

        if (customTemplates.isNotEmpty()) {
            val sep = View(panelView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(0, 8, 0, 8) }
                setBackgroundColor(panelView.context.getColor(R.color.hud_foreground_muted))
            }
            container.addView(sep)
            menuRows.add(MenuRow(""))

            customTemplates.forEach { template ->
                menuRows.add(MenuRow(template.name, isCustom = true, isDeletable = true))
                val tv = createRow(template.name, isSpecial = true)
                container.addView(tv)
                menuRows[menuRows.lastIndex] = menuRows.last().copy(textView = tv)
            }
        }

        val sep2 = View(panelView.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 8, 0, 8) }
            setBackgroundColor(panelView.context.getColor(R.color.hud_foreground_muted))
        }
        container.addView(sep2)
        menuRows.add(MenuRow(""))

        val customLabel = panelView.context.getString(R.string.menu_item_custom)
        menuRows.add(MenuRow(customLabel, isCustom = true))
        val customTv = createRow(customLabel, isSpecial = true)
        container.addView(customTv)
        menuRows[menuRows.lastIndex] = menuRows.last().copy(textView = customTv)

        val historyLabel = panelView.context.getString(R.string.menu_item_history)
        menuRows.add(MenuRow(historyLabel, isCustom = true))
        val historyTv = createRow(historyLabel, isSpecial = true)
        container.addView(historyTv)
        menuRows[menuRows.lastIndex] = menuRows.last().copy(textView = historyTv)

        val importLabel = panelView.context.getString(R.string.menu_item_import)
        menuRows.add(MenuRow(importLabel, isCustom = true))
        val importTv = createRow(importLabel, isSpecial = true)
        container.addView(importTv)
        menuRows[menuRows.lastIndex] = menuRows.last().copy(textView = importTv)
    }

    private fun createRow(label: String, isSpecial: Boolean): TextView {
        return TextView(panelView.context).apply {
            text = panelView.context.getString(R.string.menu_row, label)
            typeface = Typeface.MONOSPACE
            textSize = if (isSpecial) 18f else 22f
            setTextColor(panelView.context.getColor(
                if (isSpecial) R.color.hud_accent else R.color.hud_foreground_muted
            ))
            setPadding(0, 10, 0, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun render() {
        val validIndices = menuRows.indices.filter { menuRows[it].label.isNotEmpty() }
        val currentPos = validIndices.indexOf(focusedIndex)

        val windowStart = if (currentPos < 0) 0 else {
            (currentPos - (visibleRows / 2)).coerceAtLeast(0)
                .coerceAtMost((validIndices.size - visibleRows).coerceAtLeast(0))
        }
        val windowEnd = (windowStart + visibleRows).coerceAtMost(validIndices.size)

        menuRows.forEachIndexed { index, row ->
            val tv = row.textView ?: return@forEachIndexed
            if (row.label.isEmpty()) {
                tv.visibility = View.GONE
                return@forEachIndexed
            }
            val posInValid = validIndices.indexOf(index)
            if (posInValid < windowStart || posInValid >= windowEnd) {
                tv.visibility = View.GONE
                return@forEachIndexed
            }
            val isFocused = index == focusedIndex
            tv.visibility = View.VISIBLE
            val isDeleteVisual = isFocused && row.isDeletable && deleteArmed
            tv.text = if (isDeleteVisual) "> Delete ${row.label}?" else if (isFocused) "> ${row.label}" else "  ${row.label}"
            tv.typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (isFocused) Typeface.BOLD else Typeface.NORMAL
            )
            tv.setTextColor(panelView.context.getColor(
                when {
                    isDeleteVisual -> R.color.hud_foreground
                    isFocused && row.isCustom -> R.color.hud_accent
                    isFocused && row.isActive -> R.color.hud_foreground
                    isFocused -> R.color.hud_foreground
                    row.isCustom -> R.color.hud_accent
                    else -> R.color.hud_foreground_muted
                }
            ))
        }
    }

    private fun seedStartIndex(): Int = if (activeSessionName != null) 1 else 0

    override fun handleAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                quitConfirmationArmed = false
                if (deleteArmed) {
                    deleteArmed = false
                    return ScreenCommand.Stay
                }
                val row = menuRows.getOrNull(focusedIndex) ?: return ScreenCommand.Stay

                if (row.isActive) {
                    val activity = panelView.context as MainActivity
                    activity.resumeSession()
                    return ScreenCommand.Stay
                }

                val seedStart = seedStartIndex()
                val seedEnd = seedStart + seedLabels.size
                when {
                    focusedIndex in seedStart until seedEnd -> {
                        val activity = panelView.context as MainActivity
                        activity.startWorkout(focusedIndex - seedStart)
                    }
                    row.label == panelView.context.getString(R.string.menu_item_custom) -> {
                        return ScreenCommand.Open(ScreenId.CUSTOM_WORKOUT)
                    }
                    row.label == panelView.context.getString(R.string.menu_item_history) -> {
                        return ScreenCommand.Open(ScreenId.HISTORY)
                    }
                    row.label == panelView.context.getString(R.string.menu_item_import) -> {
                        val activity = panelView.context as MainActivity
                        val repo = activity.getRepository()
                        val result = ImportWorkout.scanAndImport(activity, repo)
                        val msg = if (result.imported.isNotEmpty()) {
                            "Imported: ${result.imported.joinToString()}"
                        } else if (result.errors.isNotEmpty()) {
                            "Error: ${result.errors.first()}"
                        } else {
                            "No files found"
                        }
                        android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_LONG).show()
                        if (result.imported.isNotEmpty()) {
                            activity.refreshCustomWorkouts()
                            focusedIndex = 0
                            buildMenu()
                        }
                        ScreenCommand.Stay
                    }
                    row.isCustom && row.label.isNotEmpty() -> {
                        val template = customTemplates.find { it.name == row.label }
                        if (template != null) {
                            val activity = panelView.context as MainActivity
                            activity.startCustomWorkout(template)
                        }
                    }
                }
                ScreenCommand.Stay
            }

            NavigationAction.BACK -> {
                val row = menuRows.getOrNull(focusedIndex)
                if (row?.isDeletable == true && deleteArmed) {
                    deleteArmed = false
                    val activity = panelView.context as MainActivity
                    activity.getRepository().deleteCustomTemplate(row.label)
                    activity.refreshCustomWorkouts()
                    focusedIndex = 0
                    buildMenu()
                    ScreenCommand.Stay
                } else if (row?.isDeletable == true) {
                    deleteArmed = true
                    ScreenCommand.Stay
                } else if (quitConfirmationArmed) {
                    ScreenCommand.ExitApp
                } else {
                    quitConfirmationArmed = true
                    ScreenCommand.Stay
                }
            }

            NavigationAction.NEXT -> {
                quitConfirmationArmed = false
                deleteArmed = false
                val valid = menuRows.indices.filter { menuRows[it].label.isNotEmpty() }
                val currentPos = valid.indexOf(focusedIndex)
                if (currentPos >= 0 && currentPos < valid.lastIndex) {
                    focusedIndex = valid[currentPos + 1]
                }
                ScreenCommand.Stay
            }

            NavigationAction.PREVIOUS -> {
                quitConfirmationArmed = false
                deleteArmed = false
                val valid = menuRows.indices.filter { menuRows[it].label.isNotEmpty() }
                val currentPos = valid.indexOf(focusedIndex)
                if (currentPos > 0) {
                    focusedIndex = valid[currentPos - 1]
                }
                ScreenCommand.Stay
            }
        }
    }

    override fun navigationHint(context: Context): String {
        val row = menuRows.getOrNull(focusedIndex)
        return when {
            deleteArmed && row?.isDeletable == true -> "Double-tap again to delete"
            quitConfirmationArmed -> context.getString(R.string.nav_menu_quit_confirm)
            else -> context.getString(R.string.nav_menu_default)
        }
    }
}
