package com.example.myfit.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromWorkoutSetList(value: List<WorkoutSet>?): String {
        return gson.toJson(value ?: emptyList<WorkoutSet>())
    }

    @TypeConverter
    fun toWorkoutSetList(value: String?): List<WorkoutSet> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<WorkoutSet>>() {}.type
        return gson.fromJson(value, listType)
    }
}