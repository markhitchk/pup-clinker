package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Shared chrome only: each game's board and interaction geometry remain independent. */
@Composable
internal fun PuppyCasinoScrollableColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
internal fun PuppyCasinoHelp(content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics {
        stateDescription = if (expanded) "Expanded" else "Collapsed"
    }) { Text(if (expanded) "Rules & help ▴" else "Rules & help ▾") }
    if (expanded) content()
}
