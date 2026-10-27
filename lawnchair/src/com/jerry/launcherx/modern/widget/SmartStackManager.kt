package com.jerry.launcherx.modern.widget

import android.content.Context
import android.content.SharedPreferences

class SmartStackManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "smart_stack_prefs", Context.MODE_PRIVATE
    )

    data class StackConfig(
        val stackId: String,
        val widgetIds: List<Int>,
        val autoRotate: Boolean = true,
        val rotateIntervalMs: Long = 30_000L,
        val currentIndex: Int = 0
    )

    private val stacks = mutableMapOf<String, StackConfig>()

    fun createStack(
        stackId: String,
        widgetIds: List<Int>,
        autoRotate: Boolean = true,
        rotateIntervalMs: Long = 30_000L
    ): StackConfig {
        val config = StackConfig(
            stackId = stackId,
            widgetIds = widgetIds,
            autoRotate = autoRotate,
            rotateIntervalMs = rotateIntervalMs
        )
        stacks[stackId] = config
        saveStackConfig(config)
        return config
    }

    fun addWidgetToStack(stackId: String, widgetId: Int) {
        val config = stacks[stackId] ?: return
        val newWidgetIds = config.widgetIds.toMutableList()
        if (!newWidgetIds.contains(widgetId)) {
            newWidgetIds.add(widgetId)
            val updated = config.copy(widgetIds = newWidgetIds)
            stacks[stackId] = updated
            saveStackConfig(updated)
        }
    }

    fun removeWidgetFromStack(stackId: String, widgetId: Int) {
        val config = stacks[stackId] ?: return
        val newWidgetIds = config.widgetIds.toMutableList()
        newWidgetIds.remove(widgetId)
        if (newWidgetIds.isEmpty()) {
            removeStack(stackId)
        } else {
            val updated = config.copy(widgetIds = newWidgetIds)
            stacks[stackId] = updated
            saveStackConfig(updated)
        }
    }

    fun removeStack(stackId: String) {
        stacks.remove(stackId)
        prefs.edit().remove("stack_$stackId").apply()
    }

    fun getStackConfig(stackId: String): StackConfig? = stacks[stackId]

    fun getAllStacks(): Map<String, StackConfig> = stacks.toMap()

    fun updateCurrentIndex(stackId: String, index: Int) {
        val config = stacks[stackId] ?: return
        val updated = config.copy(currentIndex = index)
        stacks[stackId] = updated
        saveStackConfig(updated)
    }

    fun toggleAutoRotate(stackId: String) {
        val config = stacks[stackId] ?: return
        val updated = config.copy(autoRotate = !config.autoRotate)
        stacks[stackId] = updated
        saveStackConfig(updated)
    }

    private fun saveStackConfig(config: StackConfig) {
        val prefsKey = "stack_${config.stackId}"
        prefs.edit().apply {
            putString("${prefsKey}_widgetIds", config.widgetIds.joinToString(","))
            putBoolean("${prefsKey}_autoRotate", config.autoRotate)
            putLong("${prefsKey}_rotateInterval", config.rotateIntervalMs)
            putInt("${prefsKey}_currentIndex", config.currentIndex)
            apply()
        }
    }

    fun loadAllStacks() {
        val allPrefs = prefs.all
        val stackIds = allPrefs.keys
            .filter { it.endsWith("_widgetIds") }
            .map { it.removeSuffix("_widgetIds").removePrefix("stack_") }
            .distinct()

        for (id in stackIds) {
            val widgetIdsStr = prefs.getString("stack_${id}_widgetIds", "") ?: ""
            val widgetIds = widgetIdsStr.split(",").mapNotNull { it.toIntOrNull() }
            if (widgetIds.isNotEmpty()) {
                stacks[id] = StackConfig(
                    stackId = id,
                    widgetIds = widgetIds,
                    autoRotate = prefs.getBoolean("stack_${id}_autoRotate", true),
                    rotateIntervalMs = prefs.getLong("stack_${id}_rotateInterval", 30_000L),
                    currentIndex = prefs.getInt("stack_${id}_currentIndex", 0)
                )
            }
        }
    }
}
