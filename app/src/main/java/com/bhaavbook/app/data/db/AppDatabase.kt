package com.bhaavbook.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bhaavbook.app.data.model.Brand
import com.bhaavbook.app.data.model.Category
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductFts
import com.bhaavbook.app.data.model.ProductVariant

@Database(
    entities = [
        Product::class,
        ProductFts::class,
        ProductVariant::class,
        Brand::class,
        Category::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun brandDao(): BrandDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val DATABASE_NAME = "bhaavbook.db"
    }
}
