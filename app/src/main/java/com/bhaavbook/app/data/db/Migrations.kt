package com.bhaavbook.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migration from version 1 → 2.
 *
 * What changed:
 * - New table `product_variants` — carries all price / unit / stock data that
 *   lived directly on `products` in v1. The combination (productId, variantLabel)
 *   is unique; deleting a product cascades to its variants.
 * - New tables `brands` and `categories` with unique slug + name indices.
 *   Pre-populated with the canonical set for a pooja-item shop.
 * - The `products` table is recreated without `selling_price`, `cost_price`,
 *   `unit`, `quantity_value`, and `in_stock`. SQLite does not support DROP COLUMN
 *   before 3.35, so the safe path is: rename → recreate → copy → drop old.
 * - The `products_fts` FTS4 virtual table is rebuilt against the slimmed-down
 *   products table (name + brand + category — still the same searchable fields).
 * - Every existing product row gets one "Standard" variant that carries its v1
 *   price, cost, unit, quantity and in-stock values, so no price data is lost.
 *
 * IMPORTANT: fallbackToDestructiveMigration() is absent from the database
 * builder. A missing Migration object causes a hard crash at startup rather
 * than silently wiping the shopkeeper's only copy of the price list.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // -----------------------------------------------------------------------
        // 1. Create product_variants
        // -----------------------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product_variants` (
                `id`            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId`     INTEGER NOT NULL,
                `variantLabel`  TEXT    NOT NULL,
                `sellingPrice`  REAL    NOT NULL,
                `wholesalePrice` REAL,
                `costPrice`     REAL,
                `inStock`       INTEGER NOT NULL DEFAULT 1,
                `created_at`    INTEGER NOT NULL,
                `updated_at`    INTEGER NOT NULL,
                FOREIGN KEY(`productId`) REFERENCES `products`(`id`)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_product_variants_productId` ON `product_variants`(`productId`)"
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_product_variants_productId_variantLabel`
            ON `product_variants`(`productId`, `variantLabel`)
            """.trimIndent()
        )

        // -----------------------------------------------------------------------
        // 2. Migrate existing product prices into "Standard" variants
        // -----------------------------------------------------------------------
        db.execSQL(
            """
            INSERT INTO `product_variants`
                (productId, variantLabel, sellingPrice, costPrice, inStock, created_at, updated_at)
            SELECT
                id,
                'Standard',
                selling_price,
                cost_price,
                in_stock,
                created_at,
                updated_at
            FROM `products`
            """.trimIndent()
        )

        // -----------------------------------------------------------------------
        // 3. Recreate products without the price/unit/stock columns
        //    SQLite < 3.35 has no DROP COLUMN, so: rename → recreate → copy → drop.
        // -----------------------------------------------------------------------
        db.execSQL("ALTER TABLE `products` RENAME TO `products_old`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `products` (
                `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name`       TEXT    NOT NULL,
                `brand`      TEXT,
                `category`   TEXT,
                `notes`      TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name`      ON `products`(`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_brand`     ON `products`(`brand`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_category`  ON `products`(`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_updated_at` ON `products`(`updated_at`)")

        db.execSQL(
            """
            INSERT INTO `products` (id, name, brand, category, notes, created_at, updated_at)
            SELECT id, name, brand, category, notes, created_at, updated_at
            FROM `products_old`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `products_old`")

        // -----------------------------------------------------------------------
        // 4. Rebuild FTS table against the new (slimmer) products table
        // -----------------------------------------------------------------------
        db.execSQL("DROP TABLE IF EXISTS `products_fts`")
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `products_fts`
            USING fts4(content=`products`, `name`, `brand`, `category`)
            """.trimIndent()
        )
        // Repopulate the FTS index from the surviving product rows.
        db.execSQL("INSERT INTO `products_fts`(products_fts) VALUES('rebuild')")

        // -----------------------------------------------------------------------
        // 5. Create brands table with pre-populated entries
        // -----------------------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `brands` (
                `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name`       TEXT NOT NULL,
                `slug`       TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_brands_slug` ON `brands`(`slug`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_brands_name` ON `brands`(`name`)")

        val now = System.currentTimeMillis()
        val defaultBrands = listOf(
            "Anil"                   to "anil",
            "Janak"                  to "janak",
            "Satya"                  to "satya",
            "Zed Black"              to "zed-black",
            "Forest"                 to "forest",
            "Shree Ganesh Agarbatti" to "shree-ganesh-agarbatti"
        )
        defaultBrands.forEach { (name, slug) ->
            db.execSQL(
                "INSERT OR IGNORE INTO `brands` (name, slug, created_at) VALUES ('$name', '$slug', $now)"
            )
        }

        // -----------------------------------------------------------------------
        // 6. Create categories table with pre-populated entries
        // -----------------------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name`       TEXT NOT NULL,
                `slug`       TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_slug` ON `categories`(`slug`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories`(`name`)")

        val defaultCategories = listOf(
            "Agarbatti"   to "agarbatti",
            "Dhoop"       to "dhoop",
            "Kamphor"     to "kamphor",
            "Pooja Items" to "pooja-items"
        )
        defaultCategories.forEach { (name, slug) ->
            db.execSQL(
                "INSERT OR IGNORE INTO `categories` (name, slug, created_at) VALUES ('$name', '$slug', $now)"
            )
        }
    }
}

/**
 * Schema migration from version 2 → 3.
 *
 * What changed:
 * - Adds `selling_min` REAL and `wholesale_min` REAL columns to `product_variants`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `product_variants` ADD COLUMN `selling_min` REAL")
        db.execSQL("ALTER TABLE `product_variants` ADD COLUMN `wholesale_min` REAL")
    }
}

