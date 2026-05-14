package com.example.allaboutmusic.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/allaboutmusic.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}
