package com.bhaavbook.app.di

import android.content.Context
import androidx.room.Room
import com.bhaavbook.app.data.db.AppDatabase
import com.bhaavbook.app.data.db.BrandDao
import com.bhaavbook.app.data.db.CategoryDao
import com.bhaavbook.app.data.db.MIGRATION_1_2
import com.bhaavbook.app.data.db.MIGRATION_2_3
import com.bhaavbook.app.data.db.MIGRATION_3_4
import com.bhaavbook.app.data.db.ProductDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that lives as long as the process, for writes that must complete even
 * if the screen that started them is gone — committing a delete after its undo
 * window, for instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

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
            // No fallbackToDestructiveMigration: this database *is* the
            // shopkeeper's price list and there is no cloud copy of it. Bumping
            // the schema version without supplying a Migration must fail loudly
            // in development, never silently wipe a live install.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

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

    // SettingsRepository, ProductRepository, BrandRepository, CategoryRepository,
    // CsvParser, CsvImporter and CsvExporter are @Singleton with @Inject
    // constructors — Hilt resolves them without an explicit @Provides.
}
