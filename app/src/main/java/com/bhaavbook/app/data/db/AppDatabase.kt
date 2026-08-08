package com.bhaavbook.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductFts

@Database(
    entities = [Product::class, ProductFts::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    companion object {
        const val DATABASE_NAME = "bhaavbook.db"
    }
}
