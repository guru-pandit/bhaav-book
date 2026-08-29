package com.bhaavbook.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.model.ProductWithVariants
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.ui.theme.TabularFigures

/**
 * The whole point of the app: hold the phone up and let the customer read the
 * price from arm's length.
 *
 * Single variant  → price displayed directly (no extra tap, no regression).
 * Multiple variants → horizontal chip row; tapping a chip sets the hero price.
 *
 * Layout priority, largest to smallest: price → item name → variant chips →
 * pack label → wholesale → notes → actions.
 * Edit and Delete are deliberately quiet — they are the shopkeeper's tools, and
 * a big red Delete next to a customer's face is an accident waiting to happen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceSheet(
    pwv: ProductWithVariants,
    currencySymbol: String,
    priceFontSize: PriceFontSize,
    showWholesalePrice: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val variants = pwv.variants

    // Track the selected variant — default to first, persisted across
    // recompositions but not across sheet open/close (rememberSaveable
    // with the product id as key resets when a different product opens).
    var selectedVariantId by rememberSaveable(pwv.product.id) {
        mutableStateOf(variants.firstOrNull()?.id ?: 0L)
    }
    val selected: ProductVariant? = variants.find { it.id == selectedVariantId }
        ?: variants.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand label
            if (!pwv.product.brand.isNullOrBlank()) {
                Text(
                    text = pwv.product.brand.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.tertiary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }

            // Product name
            Text(
                text = pwv.product.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Variant chip row — only shown when there are multiple variants
            if (variants.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(variants) { variant ->
                        FilterChip(
                            selected = variant.id == selected?.id,
                            onClick = { selectedVariantId = variant.id },
                            label = { Text(variant.variantLabel) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Hero price card
            if (selected != null) {
                val price = com.bhaavbook.app.format.formatPriceRange(selected.sellingMin, selected.sellingPrice, currencySymbol)
                PriceHero(
                    price = price,
                    packLabel = selected.variantLabel,
                    baseSize = priceFontSize.heroSize,
                    inStock = selected.inStock
                )

                // Wholesale price row
                if (showWholesalePrice && selected.wholesalePrice != null) {
                    val wholesalePriceStr = com.bhaavbook.app.format.formatPriceRange(selected.wholesaleMin, selected.wholesalePrice, currencySymbol)
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.secondaryContainer
                    ) {
                        Text(
                            text = "Wholesale: $wholesalePriceStr",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Notes
            pwv.product.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.note_label, note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelLarge)
                }

                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.error
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * The price card. The number shrinks as it gets longer so `₹1,25,000` fits the
 * same card as `₹45` without ever wrapping or clipping.
 */
@Composable
private fun PriceHero(
    price: String,
    packLabel: String,
    baseSize: Int,
    inStock: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val fittedSize = fitPriceSize(baseSize, price.length)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = price,
                fontSize = fittedSize.sp,
                lineHeight = (fittedSize * 1.1f).sp,
                fontWeight = FontWeight.Black,
                color = colors.primary,
                style = TabularFigures,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.per_unit, packLabel),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant
            )

            if (!inStock) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.errorContainer
                ) {
                    Text(
                        text = stringResource(R.string.out_of_stock),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Steps the hero price down once the string outgrows the card width.
 */
internal fun fitPriceSize(baseSize: Int, length: Int): Int {
    val scale = when {
        length <= 6 -> 1.0f
        length <= 8 -> 0.82f
        length <= 10 -> 0.68f
        length <= 12 -> 0.56f
        else -> 0.46f
    }
    return (baseSize * scale).toInt().coerceAtLeast(24)
}

private val PriceFontSize.heroSize: Int
    get() = when (this) {
        PriceFontSize.NORMAL -> 56
        PriceFontSize.LARGE -> 68
        PriceFontSize.EXTRA_LARGE -> 80
    }
