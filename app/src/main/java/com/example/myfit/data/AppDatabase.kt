package com.example.myfit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myfit.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        WorkoutTask::class,
        ExerciseTemplate::class,
        ScheduleConfig::class,
        WeightRecord::class,
        AppSetting::class,
        WeeklyRoutineItem::class
    ],
    version = 5, // 升级版本号以触发重建，确保数据找回
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "myfit_v5.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(PrepopulateCallback())
                    .build().also { instance = it }
            }
        }
    }

    private class PrepopulateCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            instance?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.workoutDao()

                    // 1. 初始化设置
                    dao.saveAppSettings(AppSetting(themeId = 0))

                    // 2. 初始化周类型配置
                    if (dao.getScheduleCount() == 0) {
                        val types = listOf(DayType.CORE, DayType.CORE, DayType.ACTIVE_REST, DayType.CORE, DayType.CORE, DayType.LIGHT, DayType.REST)
                        types.forEachIndexed { index, type -> dao.insertSchedule(ScheduleConfig(index + 1, type)) }
                    }

                    // 3. 找回动作库 (如果没有数据则写入)
                    if (dao.getTemplateCount() == 0) {
                        val defaults = listOf(
                            ExerciseTemplate(name = "坐姿推胸", defaultTarget = "3组x12次", category = "STRENGTH"),
                            ExerciseTemplate(name = "高位下拉", defaultTarget = "3组x12次", category = "STRENGTH"),
                            ExerciseTemplate(name = "深蹲", defaultTarget = "4组x10次", category = "STRENGTH"),
                            ExerciseTemplate(name = "硬拉", defaultTarget = "4组x8次", category = "STRENGTH"),
                            ExerciseTemplate(name = "哑铃侧平举", defaultTarget = "4组x15次", category = "STRENGTH"),
                            ExerciseTemplate(name = "慢跑", defaultTarget = "30分钟", category = "CARDIO"),
                            ExerciseTemplate(name = "椭圆仪", defaultTarget = "20分钟", category = "CARDIO"),
                            ExerciseTemplate(name = "划船机", defaultTarget = "15分钟", category = "CARDIO")
                        )
                        defaults.forEach { dao.insertTemplate(it) }
                    }
                }
            }
        }
    }
}