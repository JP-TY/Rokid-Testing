package com.rokid.workouttracker

import android.content.Context
import android.view.View
import android.widget.TextView

internal class CompleteScreenController(
    panelView: View
) : ViewScreenController(ScreenId.COMPLETE, panelView) {

    private val completeMessageView: TextView = panelView.findViewById(R.id.completeMessageView)
    private val completeDurationView: TextView = panelView.findViewById(R.id.completeDurationView)
    private val completeStatsView: TextView = panelView.findViewById(R.id.completeStatsView)
    private var exportArmed = false

    override fun onEnter() {
        exportArmed = false
        val activity = panelView.context as MainActivity
        val duration = activity.getLastWorkoutDuration()
        val minutes = duration / 60
        val seconds = duration % 60
        completeDurationView.text = "Duration: %d:%02d".format(minutes, seconds)

        val lastResult = activity.getLastWorkoutResult()
        if (lastResult != null) {
            val volUnit = if (lastResult.weightUnit == WeightUnit.KG) "kg" else "lbs"
            val volStr = if (lastResult.totalVolume > 0) {
                "Volume: %.0f $volUnit".format(lastResult.totalVolume)
            } else ""
            val setsStr = "Sets: ${lastResult.totalSets}"
            completeStatsView.text = "$setsStr  $volStr"
            completeStatsView.visibility = View.VISIBLE
        } else {
            completeStatsView.visibility = View.GONE
        }
    }

    override fun handleAction(action: NavigationAction): ScreenCommand {
        return when (action) {
            NavigationAction.SELECT -> {
                exportArmed = false
                ScreenCommand.Open(ScreenId.MENU)
            }

            NavigationAction.BACK -> {
                if (exportArmed) {
                    exportArmed = false
                    val activity = panelView.context as MainActivity
                    activity.exportLastWorkout()
                    ScreenCommand.Stay
                } else {
                    exportArmed = true
                    ScreenCommand.Stay
                }
            }

            else -> ScreenCommand.Stay
        }
    }

    override fun navigationHint(context: Context): String {
        return if (exportArmed) {
            context.getString(R.string.nav_complete_export_confirm)
        } else {
            context.getString(R.string.nav_complete)
        }
    }
}
