package com.bhaavbook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.BuildConfig
import com.bhaavbook.app.R
import com.bhaavbook.app.csv.CsvExporter
import com.bhaavbook.app.data.model.ProductUnit
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.data.settings.ThemeOption
import com.bhaavbook.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Currency Symbol ---
            SectionHeader("Display & Preferences")
            OutlinedTextField(
                value = settings.currencySymbol,
                onValueChange = { if (it.length <= 3) viewModel.updateCurrencySymbol(it) },
                label = { Text("Currency Symbol") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // --- Default Unit ---
            UnitSettingDropdown(
                selectedUnitName = settings.defaultUnit,
                onSelectUnit = viewModel::updateDefaultUnit
            )

            // --- Auto focus search ---
            SettingSwitchRow(
                title = "Auto-focus search on open",
                subtitle = "Keyboard opens immediately on launch for faster lookup",
                checked = settings.autoFocusSearch,
                onCheckedChange = viewModel::updateAutoFocusSearch
            )

            // --- Show cost price ---
            SettingSwitchRow(
                title = "Show cost price",
                subtitle = "Display purchase cost in price detail sheet (hidden from customers by default)",
                checked = settings.showCostPrice,
                onCheckedChange = viewModel::updateShowCostPrice
            )

            HorizontalDivider()

            // --- Price Font Size ---
            SectionHeader("Price Display Font Size")
            listOf(
                PriceFontSize.NORMAL to "Normal (48 sp)",
                PriceFontSize.LARGE to "Large (60 sp)",
                PriceFontSize.EXTRA_LARGE to "Extra Large (72 sp)"
            ).forEach { (option, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updatePriceFontSize(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.priceFontSize == option,
                        onClick = { viewModel.updatePriceFontSize(option) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()

            // --- Theme Option ---
            SectionHeader("Theme")
            listOf(
                ThemeOption.SYSTEM to "System Default",
                ThemeOption.LIGHT to "Light Mode",
                ThemeOption.DARK to "Dark Mode"
            ).forEach { (option, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateTheme(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.theme == option,
                        onClick = { viewModel.updateTheme(option) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()

            // --- About Section ---
            SectionHeader("About BhaavBook")
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
            label = { Text("Default Unit for New Products") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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
