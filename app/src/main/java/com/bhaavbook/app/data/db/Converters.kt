package com.bhaavbook.app.data.db

import androidx.room.TypeConverter
import com.bhaavbook.app.data.model.ProductUnit

class Converters {
    @TypeConverter
    fun fromProductUnit(unit: ProductUnit): String = unit.name

    @TypeConverter
    fun toProductUnit(value: String): ProductUnit = ProductUnit.fromString(value)
}
