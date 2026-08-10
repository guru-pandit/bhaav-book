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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.format.toPriceString
import com.bhaavbook.app.ui.theme.TabularFigures

/**
 * The whole point of the app: hold the phone up and let the customer read the
 * price from arm's length.
 *
 * Layout priority, largest to smallest: price → item name → pack size →
 * everything else. Edit and Delete are deliberately quiet text-weight buttons —
 * they are the shopkeeper's tools, and a big red Delete next to a customer's
 * face is an accident waiting to happen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceSheet(
    product: Product,
    currencySymbol: String,
    priceFontSize: PriceFontSize,
    showCostPrice: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val price = product.sellingPrice.toPriceString(currencySymbol)

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
            if (!product.brand.isNullOrBlank()) {
                Text(
                    text = product.brand.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.tertiary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = product.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            PriceHero(
                price = price,
                packLabel = product.packLabel,
                baseSize = priceFontSize.heroSize,
                inStock = product.inStock
            )

            if (showCostPrice && product.costPrice != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.tertiaryContainer
                ) {
                    Text(
                        text = stringResource(
                            R.string.cost_price_label,
                            product.costPrice.toPriceString(currencySymbol)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            product.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.note_label, note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

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
 * same card as `₹45` without ever wrapping or clipping — Compose 1.7 has no
 * auto-sizing text, and letting a hero price ellipsise would be worse than any
 * amount of arithmetic here.
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
 * Steps the hero price down once the string outgrows the card width. Six
 * characters (`₹1,200`) fit at full size on the narrowest phone we target.
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
