package com.bhaavbook.app.data.repository

import com.bhaavbook.app.data.db.BrandDao
import com.bhaavbook.app.data.model.Brand
import com.bhaavbook.app.data.model.generateSlug
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrandRepository @Inject constructor(
    private val dao: BrandDao
) {

    fun getAllBrands(): Flow<List<Brand>> = dao.getAllBrands()

    suspend fun getBySlug(slug: String): Brand? = dao.getBySlug(slug)

    suspend fun getByName(name: String): Brand? = dao.getByName(name)

    /**
     * Resolves a brand from a CSV row using slug-first resolution:
     *
     * 1. [slug] present and non-blank → look up by slug.
     *    - Found: verify display name matches. If not, record a mismatch warning
     *      (caller handles) and return the existing brand.
     *    - Not found: create a new Brand with that slug and the supplied [name]
     *      (or the slug itself if [name] is blank).
     * 2. [slug] absent/blank, [name] present → generate slug from name,
     *    then look up or create as in step 1.
     * 3. Both blank → return null.
     *
     * Returns a [BrandResolution] that carries the resolved [Brand] and an
     * optional warning message for the error CSV.
     */
    suspend fun resolveFromCsv(slug: String?, name: String?): BrandResolution {
        val slugTrimmed = slug?.trim().orEmpty()
        val nameTrimmed = name?.trim().orEmpty()

        if (slugTrimmed.isEmpty() && nameTrimmed.isEmpty()) return BrandResolution(null, null)

        val effectiveSlug = if (slugTrimmed.isNotEmpty()) slugTrimmed
        else generateSlug(nameTrimmed)

        val existing = dao.getBySlug(effectiveSlug)
        if (existing != null) {
            val warning = if (nameTrimmed.isNotEmpty() &&
                !existing.name.equals(nameTrimmed, ignoreCase = true)
            ) {
                "brand slug mismatch: slug '$effectiveSlug' already mapped to " +
                    "'${existing.name}', ignoring supplied name '$nameTrimmed'"
            } else null
            return BrandResolution(existing, warning)
        }

        // Not found — create new entry
        val displayName = nameTrimmed.ifEmpty { effectiveSlug }
        val newBrand = Brand(name = displayName, slug = effectiveSlug)
        dao.upsert(newBrand)
        val saved = dao.getBySlug(effectiveSlug) ?: Brand(name = displayName, slug = effectiveSlug)
        return BrandResolution(saved, null)
    }

    /**
     * Saves a new brand with automatic slug generation and collision avoidance.
     * If the auto-generated slug already exists, appends "-2", "-3", etc.
     */
    suspend fun save(name: String, customSlug: String? = null): Brand {
        val baseSlug = customSlug?.trim() ?: generateSlug(name)
        val uniqueSlug = findUniqueSlug(baseSlug)
        val brand = Brand(name = name.trim(), slug = uniqueSlug)
        dao.upsert(brand)
        return dao.getBySlug(uniqueSlug) ?: brand
    }

    suspend fun update(brand: Brand) = dao.upsert(brand)

    suspend fun delete(brand: Brand) = dao.delete(brand)

    /**
     * Finds the lowest-numbered slug variant that does not yet exist in the DB.
     * e.g. if "cycle" and "cycle-2" both exist, returns "cycle-3".
     */
    suspend fun findUniqueSlug(base: String): String {
        val existing = dao.getSlugsWithPrefix(base).map { it.slug }.toSet()
        if (base !in existing) return base
        var counter = 2
        while ("$base-$counter" in existing) counter++
        return "$base-$counter"
    }
}

data class BrandResolution(
    val brand: Brand?,
    /** Non-null when the slug was reused but the display name differed. */
    val warning: String?
)
