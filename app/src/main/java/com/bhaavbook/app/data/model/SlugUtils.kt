package com.bhaavbook.app.data.model

/**
 * Converts a human-readable display name into a stable, URL-safe slug.
 *
 * Rules (applied in order):
 * 1. Trim leading/trailing whitespace.
 * 2. Lowercase everything.
 * 3. Strip any character that is not a letter, digit, space, or hyphen.
 * 4. Collapse runs of whitespace into a single hyphen.
 * 5. Collapse consecutive hyphens into one.
 * 6. Strip any trailing hyphen left by step 3–5.
 *
 * Examples:
 * | Input               | Output              |
 * |---------------------|---------------------|
 * | Cycle               | cycle               |
 * | Zed Black           | zed-black           |
 * | Puja Items          | puja-items          |
 * | Pooja Samagri       | pooja-samagri       |
 * | M&B Fragrances      | mb-fragrances       |
 * | ---Weird---Name---  | weird-name          |
 */
fun generateSlug(input: String): String =
    input
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")   // strip non-alphanumeric except spaces/hyphens
        .replace(Regex("\\s+"), "-")             // spaces → hyphens
        .replace(Regex("-{2,}"), "-")            // collapse double hyphens
        .trimEnd('-')
        .trimStart('-')

/**
 * Validates a manually-entered slug.
 *
 * Valid: 2–60 characters, only lowercase letters, digits, and hyphens,
 * must not start or end with a hyphen.
 */
fun isValidSlug(slug: String): Boolean =
    slug.length in 2..60 &&
        slug.matches(Regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$"))
