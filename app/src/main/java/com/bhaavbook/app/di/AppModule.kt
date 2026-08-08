package com.bhaavbook.app.di

import android.content.Context
import androidx.room.Room
import com.bhaavbook.app.data.db.AppDatabase
import com.bhaavbook.app.data.db.ProductDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // Add proper Migration objects before releasing v2+
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    // SettingsRepository, ProductRepository, CsvParser, CsvImporter, CsvExporter
    // are all @Singleton + @Inject constructor — Hilt resolves them automatically.
}
