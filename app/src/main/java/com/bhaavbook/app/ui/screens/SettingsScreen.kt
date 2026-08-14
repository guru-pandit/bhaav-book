package com.bhaavbook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.BuildConfig
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.ProductUnit
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.data.settings.ThemeOption
import com.bhaavbook.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onManageBrands: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let(viewModel::exportToCsvUri) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.text) }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.secondary,
                    titleContentColor = colors.onSecondary,
                    navigationIconContentColor = colors.onSecondary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader(R.string.settings_display)

            CurrencySymbolField(
                stored = settings.currencySymbol,
                onCommit = viewModel::updateCurrencySymbol
            )

            UnitSettingDropdown(
                selectedUnitName = settings.defaultUnit,
                onSelectUnit = viewModel::updateDefaultUnit
            )

            Spacer(Modifier.height(4.dp))

            SwitchRow(
                titleRes = R.string.settings_autofocus,
                subtitleRes = R.string.settings_autofocus_sub,
                checked = settings.autoFocusSearch,
                onCheckedChange = viewModel::updateAutoFocusSearch
            )

            SwitchRow(
                titleRes = R.string.settings_show_cost,
                subtitleRes = R.string.settings_show_cost_sub,
                checked = settings.showCostPrice,
                onCheckedChange = viewModel::updateShowCostPrice
            )

            SwitchRow(
                titleRes = R.string.settings_show_wholesale,
                subtitleRes = R.string.settings_show_wholesale_sub,
                checked = settings.showWholesalePrice,
                onCheckedChange = viewModel::updateShowWholesalePrice
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onManageBrands() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_manage_brands),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_manage_brands_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Divider()
            SectionHeader(R.string.settings_price_size)

            RadioGroup(
                options = PRICE_SIZE_OPTIONS,
                selected = settings.priceFontSize,
                onSelect = viewModel::updatePriceFontSize
            )

            Divider()
            SectionHeader(R.string.settings_theme)

            RadioGroup(
                options = THEME_OPTIONS,
                selected = settings.theme,
                onSelect = viewModel::updateTheme
            )

            Divider()
            SectionHeader(R.string.settings_data)

            Button(
                onClick = { viewModel.shareCsv(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_price_list))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { exportCsvLauncher.launch(EXPORT_FILE_NAME_SETTINGS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_to_storage))
            }

            Divider()
            SectionHeader(R.string.settings_about)

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private const val EXPORT_FILE_NAME_SETTINGS = "chaitanya_stores_prices.csv"

private val PRICE_SIZE_OPTIONS = listOf(
    PriceFontSize.NORMAL to R.string.settings_price_size_normal,
    PriceFontSize.LARGE to R.string.settings_price_size_large,
    PriceFontSize.EXTRA_LARGE to R.string.settings_price_size_xl
)

private val THEME_OPTIONS = listOf(
    ThemeOption.SYSTEM to R.string.settings_theme_system,
    ThemeOption.LIGHT to R.string.settings_theme_light,
    ThemeOption.DARK to R.string.settings_theme_dark
)

// ============================================================================
// Sub-components
// ============================================================================

/**
 * Keeps its own draft of the symbol while the field has focus.
 *
 * Reading straight from DataStore meant every keystroke round-tripped to disk
 * and came back asynchronously, so the field fought the user — and clearing it
 * to retype saved an empty currency symbol.
 */
@Composable
private fun CurrencySymbolField(stored: String, onCommit: (String) -> Unit) {
    var draft by remember(stored) { mutableStateOf(stored) }

    OutlinedTextField(
        value = draft,
        onValueChange = { typed ->
            if (typed.length <= 3) {
                draft = typed
                onCommit(typed)
            }
        },
        label = { Text(stringResource(R.string.settings_currency)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun SwitchRow(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> RadioGroup(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    options.forEach { (option, labelRes) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(option) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected == option, onClick = { onSelect(option) })
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSettingDropdown(
    selectedUnitName: String,
    onSelectUnit: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentUnit = ProductUnit.fromString(selectedUnitName)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = "${currentUnit.displayName} (${currentUnit.shortLabel})",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_default_unit)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProductUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${unit.displayName} (${unit.shortLabel})") },
                    onClick = {
                        onSelectUnit(unit.name)
                        expanded = false
                    }
                )
            }
        }
    }
}
