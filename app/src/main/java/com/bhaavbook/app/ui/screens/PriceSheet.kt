package com.bhaavbook.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.ui.theme.Cream
import com.bhaavbook.app.ui.theme.CreamDark
import com.bhaavbook.app.ui.theme.Gold
import com.bhaavbook.app.ui.theme.Maroon
import com.bhaavbook.app.ui.theme.Terracotta

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
        sheetState = sheetState,
        containerColor = Cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ─── Brand Badge & Title ──────────────────────────────────────
            if (!product.brand.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Gold,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = product.brand.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Maroon,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = product.name,
                fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Maroon
            )

            product.category?.let { cat ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cat,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Hero Price Card ──────────────────────────────────────────
            val priceSp = when (priceFontSize) {
                PriceFontSize.NORMAL -> 48.sp
                PriceFontSize.LARGE -> 60.sp
                PriceFontSize.EXTRA_LARGE -> 72.sp
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Gold, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CreamDark
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$currencySymbol${product.sellingPrice.toBigPriceString()}",
                        fontSize = priceSp,
                        fontWeight = FontWeight.Black,
                        color = Terracotta,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = product.priceDisplayString(currencySymbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Maroon
                    )

                    if (showCostPrice && product.costPrice != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Gold.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "Cost: $currencySymbol${product.costPrice.toBigPriceString()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Maroon,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ─── Out of Stock / Notes ─────────────────────────────────────
            if (!product.inStock) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "⚠ Currently Out of Stock",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            product.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Note: $note",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            // ─── Action Buttons ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Terracotta,
                        contentColor = Cream
                    )
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Double.toBigPriceString(): String =
    if (this == kotlin.math.floor(this) && this < 1_000_000) this.toLong().toString()
    else "%.2f".format(this)
