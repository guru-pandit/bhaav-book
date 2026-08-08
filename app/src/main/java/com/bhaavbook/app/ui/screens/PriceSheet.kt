package com.bhaavbook.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.settings.PriceFontSize

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ─── Product title ────────────────────────────────────────────
            Text(
                text = product.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            product.category?.let { cat ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cat,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (product.quantityValue != null) {
                Text(
                    text = "${product.quantityValue.toLong()} ${product.unit.shortLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ─── Big selling price ────────────────────────────────────────
            val priceSp = when (priceFontSize) {
                PriceFontSize.NORMAL -> 48.sp
                PriceFontSize.LARGE -> 60.sp
                PriceFontSize.EXTRA_LARGE -> 72.sp
            }
            Text(
                text = "$currencySymbol${product.sellingPrice.toBigPriceString()}",
                fontSize = priceSp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // Unit label below the price
            Text(
                text = product.priceDisplayString(currencySymbol),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ─── Cost price (only when setting is ON) ─────────────────────
            if (showCostPrice && product.costPrice != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cost: $currencySymbol${product.costPrice.toBigPriceString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ─── Out of stock indicator ───────────────────────────────────
            if (!product.inStock) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "⚠ Out of stock",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ─── Notes ───────────────────────────────────────────────────
            product.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            // ─── Action buttons ───────────────────────────────────────────
            FilledTonalButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Text("  Edit", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("  Delete", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Double.toBigPriceString(): String =
    if (this == kotlin.math.floor(this) && this < 1_000_000) this.toLong().toString()
    else "%.2f".format(this)
