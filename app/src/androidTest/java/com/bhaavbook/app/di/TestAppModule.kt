package com.bhaavbook.app.di

import android.content.Context
import androidx.room.Room
import com.bhaavbook.app.data.db.AppDatabase
import com.bhaavbook.app.data.db.BrandDao
import com.bhaavbook.app.data.db.CategoryDao
import com.bhaavbook.app.data.db.ProductDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Replaces [AppModule] for every `@HiltAndroidTest`: an in-memory database
 * instead of the shopkeeper's real file, so E2E tests start empty every run
 * and never touch (or risk corrupting) data a device actually has.
 *
 * No migrations are registered — an in-memory DB is always created fresh at
 * the current schema version, so there is nothing to migrate from.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Provides
    @Singleton
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun provideBrandDao(db: AppDatabase): BrandDao = db.brandDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
