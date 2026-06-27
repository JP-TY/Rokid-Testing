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
        val textView: TextView? = null
    )

    private val menuRows = mutableListOf<MenuRow>()
    private var focusedIndex = 0
    private var quitConfirmationArmed = false
    private val container: LinearLayout = panelView.findViewById(R.id.menuContainer)
    private var customTemplates: List<WorkoutTemplate> = emptyList()
    private val visibleRows = 6

    override fun onEnter() {
        quitConfirmationArmed = false
        focusedIndex = 0
        val activity = panelView.context as MainActivity
        customTemplates = activity.getRepository().getCustomTemplates()
        buildMenu()
    }

    private fun buildMenu() {
        menuRows.clear()
        container.removeAllViews()

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
            menuRows.add(MenuRow("")) // placeholder for separator

            customTemplates.forEach { template ->
                menuRows.add(MenuRow(template.name, isCustom = true))
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
        menuRows.add(MenuRow("")) // placeholder

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
    }

    private fun createRow(label: String, isSpecial: Boolean): TextView {
        return TextView(panelView.context).apply {
            text = "  $label"
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
            tv.text = if (isFocused) "> ${row.label}" else "  ${row.label}"
            tv.typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (isFocused) Typeface.BOLD else Typeface.NORMAL
            )
            tv.setTextColor(panelView.context.getColor(
                when {
                    isFocused && row.isCustom -> R.color.hud_accent
                    isFocused -> R.color.hud_foreground
                    row.isCustom -> R.color.hud_accent
                    else -> R.color.hud_foreground_muted
                }
            ))
        }
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                quitConfirmationArmed = false
                val row = menuRows.getOrNull(focusedIndex) ?: return ScreenCommand.Stay
                when {
                    focusedIndex < seedLabels.size -> {
                        val activity = panelView.context as MainActivity
                        activity.startWorkout(focusedIndex)
                    }
                    row.label == panelView.context.getString(R.string.menu_item_custom) -> {
                        return ScreenCommand.Open(ScreenId.CUSTOM_WORKOUT)
                    }
                    row.label == panelView.context.getString(R.string.menu_item_history) -> {
                        return ScreenCommand.Open(ScreenId.HISTORY)
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
                if (quitConfirmationArmed) {
                    ScreenCommand.ExitApp
                } else {
                    quitConfirmationArmed = true
                    ScreenCommand.Stay
                }
            }

            NavigationAction.NEXT -> {
                quitConfirmationArmed = false
                val valid = menuRows.indices.filter { menuRows[it].label.isNotEmpty() }
                val currentPos = valid.indexOf(focusedIndex)
                if (currentPos >= 0 && currentPos < valid.lastIndex) {
                    focusedIndex = valid[currentPos + 1]
                }
                ScreenCommand.Stay
            }

            NavigationAction.PREVIOUS -> {
                quitConfirmationArmed = false
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
        return if (quitConfirmationArmed) {
            context.getString(R.string.nav_menu_quit_confirm)
        } else {
            context.getString(R.string.nav_menu_default)
        }
    }
}
