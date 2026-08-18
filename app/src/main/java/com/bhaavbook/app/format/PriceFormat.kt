package com.bhaavbook.app.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Anything above this is far more likely a typo than a real price. Shared by
 * manual variant entry and CSV import so a spreadsheet value is held to the
 * same sanity check as a value typed by hand.
 */
const val MAX_PRICE = 10_000_000.0

/**
 * Formats an amount the way it is written on an Indian shop counter:
 *
 *  - Indian digit grouping — three digits, then pairs: `125000` → `1,25,000`
 *  - whole rupees drop the paise entirely: `45.0` → `45`, not `45.00`
 *  - paise are kept and rounded half-up when present: `45.505` → `45.51`
 *
 * Deliberately not `NumberFormat.getCurrencyInstance(Locale("en","IN"))`:
 * that depends on the device locale and ICU version for both the grouping and
 * the symbol, and the currency symbol here is a user setting.
 */
fun Double.toPriceString(): String {
    if (isNaN() || isInfinite()) return "—"

    val scaled = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    val plain = scaled.abs().toPlainString()
    val whole = plain.substringBefore('.')
    val paise = plain.substringAfter('.', "")

    val sign = if (scaled.signum() < 0) "-" else ""
    val fraction = if (paise.isEmpty() || paise == "00") "" else ".$paise"
    return sign + groupIndian(whole) + fraction
}

/** Convenience: `"₹1,25,000"`. */
fun Double.toPriceString(currencySymbol: String): String = currencySymbol + toPriceString()

/**
 * Groups the digits of a non-negative integer string Indian-style: the last
 * three digits stay together, everything above them is split into pairs.
 */
private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val rest = digits.dropLast(3)

    val head = StringBuilder()
    var i = rest.length
    while (i > 2) {
        head.insert(0, "," + rest.substring(i - 2, i))
        i -= 2
    }
    if (i > 0) head.insert(0, rest.substring(0, i))

    return "$head,$lastThree"
}

/**
 * Formats a pack size for display: drops a trailing `.0` so `100.0` reads as
 * `100`, and keeps one decimal otherwise (`1.5 L`).
 *
 * Pinned to [Locale.ROOT] on purpose. The default locale decides what `%.1f`
 * puts between the digits, and on a phone set to a comma-decimal locale a pack
 * size would come out as `1,5` — which then fails to parse on the way back in.
 */
fun Double.toQuantityString(): String =
    if (this == Math.floor(this) && !isInfinite()) toLong().toString()
    else String.format(Locale.ROOT, "%.1f", this)

/**
 * The value to put in a text field when editing: a plain, ungrouped number,
 * because grouping separators would have to be stripped again on save.
 */
fun Double.toEditableString(): String =
    if (this == Math.floor(this) && !isInfinite()) toLong().toString()
    else BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
