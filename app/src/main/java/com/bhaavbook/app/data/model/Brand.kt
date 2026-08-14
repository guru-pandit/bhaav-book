package com.bhaavbook.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A brand (manufacturer / label) with a slug that is stable across renames.
 *
 * The slug is the canonical CSV key — once saved it must not change automatically
 * even if the display name is edited, so CSV files that were exported with the
 * old name keep working on re-import.
 *
 * Indices enforce uniqueness on both slug and name.
 */
@Entity(
    tableName = "brands",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["name"], unique = true)
    ]
)
data class Brand(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Display name, e.g. "Zed Black". */
    val name: String,

    /** Stable lowercase key, e.g. "zed-black". Set on creation; immutable after that. */
    val slug: String,

    @androidx.room.ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
