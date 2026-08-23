package com.dzhokhov.currencyrates.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dzhokhov.currencyrates.R
import com.dzhokhov.currencyrates.core.CurrencyNames
import com.dzhokhov.currencyrates.core.PickerEntry
import com.dzhokhov.currencyrates.core.PickerList
import java.util.Locale

/**
 * Полноэкранный выбор валюты: поиск с автофокусом, группа «Золото, серебро, биткоин», затем фиатные по коду.
 * Присутствующие в списке — приглушены с отметкой и не выбираются; «Назад» закрывает выбор.
 */
@Composable
fun PickerScreen(
    picker: PickerUi,
    names: CurrencyNames,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val locale: Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val group = remember(picker, locale) { PickerList.filter(picker.group, picker.query, names, locale) }
    val fiat = remember(picker, locale) { PickerList.filter(picker.fiat, picker.query, names, locale) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val back = stringResource(R.string.back)

    Scaffold(contentWindowInsets = WindowInsets.systemBars) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = back }) {
                    Text(text = "←", style = MaterialTheme.typography.titleLarge)
                }
                Text(text = stringResource(R.string.picker_title), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = picker.query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (group.isNotEmpty()) {
                    item(key = "h-group") { SectionHeader(stringResource(R.string.group_metals)) }
                    items(group, key = { "g-" + it.code }) { PickerRow(it, names.display(it.code, locale), onSelect) }
                }
                if (fiat.isNotEmpty()) {
                    item(key = "h-fiat") { SectionHeader(stringResource(R.string.group_fiat)) }
                    items(fiat, key = { "f-" + it.code }) { PickerRow(it, names.display(it.code, locale), onSelect) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun PickerRow(entry: PickerEntry, name: String, onSelect: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !entry.present) { onSelect(entry.code) }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .alpha(if (entry.present) 0.4f else 1f),
    ) {
        FlagOrBadge(entry.code, entry.kind)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.code, style = MaterialTheme.typography.titleMedium)
            Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.present) {
            Text(text = stringResource(R.string.already_added), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
