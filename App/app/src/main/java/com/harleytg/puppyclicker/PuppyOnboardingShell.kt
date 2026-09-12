package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyOnboardingShell(
    step: PuppyOnboardingStep,
    title: String,
    canGoBack: Boolean,
    primaryLabel: String?,
    primaryEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    preferViewportFit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val horizontalPadding = if (preferViewportFit) 16.dp else 20.dp
    val headerVerticalPadding = if (preferViewportFit) 8.dp else 14.dp
    val bodyVerticalPadding = if (preferViewportFit) 4.dp else 8.dp
    val footerSpacing = if (preferViewportFit) 8.dp else 16.dp
    val bodyBottomSpacing = if (preferViewportFit) 8.dp else 18.dp
    val navigationVerticalPadding = if (preferViewportFit) 8.dp else 12.dp
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = headerVerticalPadding)
                ) {
                    Text(
                        text = "Step " + (step.persistedIndex + 1) + " of 5",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        style = if (preferViewportFit) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(if (preferViewportFit) 6.dp else 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(5) { index ->
                            LinearProgressIndicator(
                                progress = {
                                    if (index <= step.persistedIndex) 1f else 0f
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = horizontalPadding, vertical = bodyVerticalPadding),
                    content = {
                        content()
                        if (footer != null) {
                            Spacer(Modifier.height(footerSpacing))
                            footer()
                        }
                        Spacer(Modifier.height(bodyBottomSpacing))
                    }
                )

                if ((canGoBack && onBack != null) || (primaryLabel != null && onPrimary != null)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 2.dp
                    ) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = horizontalPadding, vertical = navigationVerticalPadding),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (canGoBack && onBack != null) {
                                    OutlinedButton(
                                        onClick = onBack,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Back")
                                    }
                                }
                                if (primaryLabel != null && onPrimary != null) {
                                    Button(
                                        onClick = onPrimary,
                                        enabled = primaryEnabled,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(primaryLabel, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
