package com.glowupdaily.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.LocalDate

/** All personal information is stored on-device in the app's private sandbox. */
data class UserProfile(
    val name: String = "",
    val age: Int = 16,
    val heightCm: Int = 0,
    val weightKg: Double? = null,
    val activityLevel: String = "Ligera",
    val dailyMinutes: Int = 30,
    val goals: List<String> = emptyList(),
    val trainingPlace: String = "Casa",
    val equipment: String = "Sin equipamiento",
    val daysPerWeek: Int = 3,
    val sessionMinutes: Int = 30,
    val experience: String = "Principiante",
    val foodNotes: String = "",
    val hairType: String = "No indicado",
    val hairStyle: String = "",
    val profilePhotoPath: String = "",
    val darkTheme: Boolean = true
)

data class MealEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val time: String = java.time.LocalTime.now().withSecond(0).withNano(0).toString(),
    val name: String = "",
    val portion: String = "",
    val calories: Int? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val sugar: Double? = null,
    val fiber: Double? = null,
    val photoPath: String = ""
)

data class ReminderSetting(
    val id: String,
    val title: String,
    val hour: Int = 8,
    val minute: Int = 0,
    val enabled: Boolean = false
)

data class AppData(
    val profile: UserProfile? = null,
    val xp: Int = 0,
    /** Completion keys have the format yyyy-MM-dd|task-id. */
    val completions: Set<String> = emptySet(),
    val trainingSessions: List<String> = emptyList(),
    val meals: List<MealEntry> = emptyList(),
    val reminders: List<ReminderSetting> = listOf(
        ReminderSetting("morning", "Rutina de mañana", 7, 30, false),
        ReminderSetting("training", "Entrenamiento", 16, 0, false),
        ReminderSetting("habits", "Hábitos personales", 12, 0, false),
        ReminderSetting("rest", "Pausa y descanso", 19, 0, false),
        ReminderSetting("night", "Rutina de noche", 20, 30, false),
        ReminderSetting("review", "Revisión semanal", 18, 0, false)
    ),
    val skinPhotoPaths: List<String> = emptyList(),
    val careNotes: String = "",
    val hairNotes: String = ""
)

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("glow_up_daily_private", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().create()

    fun load(): AppData {
        val raw = prefs.getString("app_data", null) ?: return AppData()
        return runCatching { gson.fromJson(raw, AppData::class.java) ?: AppData() }
            .getOrElse { AppData() }
    }

    fun save(data: AppData) {
        prefs.edit().putString("app_data", gson.toJson(data)).apply()
    }

    fun exportJson(data: AppData): String = gson.toJson(data)

    fun clear() {
        prefs.edit().clear().apply()
    }
}

object ProgressRules {
    val levelNames = listOf("Beginner", "Starter", "Consistent", "Disciplined", "Advanced", "Elite", "Master", "Legendary")
    // Experience thresholds are cumulative and intentionally habit-focused.
    val thresholds = listOf(0, 100, 250, 450, 700, 1000, 1400, 1900)

    fun levelFor(xp: Int): Int {
        var level = 0
        thresholds.forEachIndexed { index, threshold -> if (xp >= threshold) level = index }
        return level
    }

    fun xpToNext(xp: Int): Int {
        val level = levelFor(xp)
        return if (level >= thresholds.lastIndex) 0 else thresholds[level + 1] - xp
    }

    fun activeDates(completions: Set<String>): Set<LocalDate> = completions.mapNotNull {
        runCatching { LocalDate.parse(it.substringBefore('|')) }.getOrNull()
    }.toSet()

    fun currentStreak(completions: Set<String>): Int {
        val active = activeDates(completions)
        var day = LocalDate.now()
        if (day !in active) day = day.minusDays(1)
        var count = 0
        while (day in active) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun completedTasksToday(completions: Set<String>): Int = completions.count {
        it.startsWith("${LocalDate.now()}|")
    }
}
