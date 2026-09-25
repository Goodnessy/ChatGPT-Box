package com.kuyermqi.quotawidget.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.context.ContextHealthSnapshot
import com.kuyermqi.quotawidget.context.ContextHealthStatus

@Composable
fun ContextHealthSection(
    snapshot: ContextHealthSnapshot?,
    isRefreshing: Boolean,
    errorMessage: String?,
    migrationPrompt: String,
    onRefresh: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(migrationPrompt, pendingPrompt) {
        if (pendingPrompt && migrationPrompt.isNotBlank()) {
            pendingPrompt = false
            showPrompt = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = snapshot != null) { showDetails = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.context_health_title), fontWeight = FontWeight.SemiBold)
                if (snapshot != null) {
                    Text(
                        text = statusEmoji(snapshot.status) + " " + statusText(snapshot.status) + " · " +
                            stringResource(R.string.context_health_approx, snapshot.estimatedPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(snapshot.status),
                    )
                } else {
                    Text(
                        text = errorMessage ?: stringResource(R.string.context_health_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (isRefreshing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(8.dp))
            } else if (snapshot == null) {
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.context_health_refresh)) }
            }
        }
    }

    if (showDetails && snapshot != null) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = {
                Column {
                    Text(stringResource(R.string.context_health_title))
                    Text(
                        text = snapshot.title.ifBlank { stringResource(R.string.context_health_current_conversation) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = statusEmoji(snapshot.status) + " " + statusText(snapshot.status) + " · " +
                            stringResource(R.string.context_health_estimated, snapshot.estimatedPercent),
                        color = statusColor(snapshot.status),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.context_health_messages, snapshot.messageCount))
                    Text(
                        stringResource(R.string.context_health_user_messages, snapshot.userMessageCount) + " · " +
                            stringResource(R.string.context_health_ai_messages, snapshot.assistantMessageCount),
                    )
                    Text(
                        stringResource(R.string.context_health_images, snapshot.imageCount) + " · " +
                            stringResource(R.string.context_health_files, snapshot.fileCount) + " · " +
                            stringResource(R.string.context_health_code, snapshot.codeBlockCount),
                    )
                    Text(stringResource(R.string.context_health_text_size, formatCharacters(snapshot.textCharacters)))
                    Text(statusDescription(snapshot.status), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.context_health_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                if (snapshot.status == ContextHealthStatus.MIGRATION_RECOMMENDED || snapshot.status == ContextHealthStatus.HIGH_RISK) {
                    Button(onClick = {
                        if (migrationPrompt.isNotBlank()) showPrompt = true
                        else { pendingPrompt = true; onRefresh() }
                    }, enabled = !isRefreshing) {
                        Text(stringResource(R.string.context_health_generate_migration))
                    }
                } else {
                    TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.context_health_close)) }
                }
            },
            dismissButton = {
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(stringResource(R.string.context_health_refresh))
                }
            },
        )
    }

    if (showPrompt && migrationPrompt.isNotBlank()) {
        MigrationPromptDialog(prompt = migrationPrompt, onDismiss = { showPrompt = false })
    }
}

@Composable
private fun MigrationPromptDialog(prompt: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.context_health_migration_title)) },
        text = {
            SelectionContainer {
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ChatGPT Box 迁移提示词", prompt))
                Toast.makeText(context, context.getString(R.string.context_health_copied), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.context_health_copy_migration)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.context_health_close)) } },
    )
}

@Composable
private fun statusText(status: ContextHealthStatus): String = when (status) {
    ContextHealthStatus.HEALTHY -> stringResource(R.string.context_health_status_healthy)
    ContextHealthStatus.LONG -> stringResource(R.string.context_health_status_long)
    ContextHealthStatus.MIGRATION_RECOMMENDED -> stringResource(R.string.context_health_status_migrate)
    ContextHealthStatus.HIGH_RISK -> stringResource(R.string.context_health_status_high_risk)
}

@Composable
private fun statusDescription(status: ContextHealthStatus): String = when (status) {
    ContextHealthStatus.HEALTHY -> stringResource(R.string.context_health_desc_healthy)
    ContextHealthStatus.LONG -> stringResource(R.string.context_health_desc_long)
    ContextHealthStatus.MIGRATION_RECOMMENDED -> stringResource(R.string.context_health_desc_migrate)
    ContextHealthStatus.HIGH_RISK -> stringResource(R.string.context_health_desc_high_risk)
}

private fun statusEmoji(status: ContextHealthStatus): String = when (status) {
    ContextHealthStatus.HEALTHY -> "🟢"
    ContextHealthStatus.LONG -> "🟡"
    ContextHealthStatus.MIGRATION_RECOMMENDED -> "🟠"
    ContextHealthStatus.HIGH_RISK -> "🔴"
}

private fun statusColor(status: ContextHealthStatus): Color = when (status) {
    ContextHealthStatus.HEALTHY -> Color(0xFF43A047)
    ContextHealthStatus.LONG -> Color(0xFFF9A825)
    ContextHealthStatus.MIGRATION_RECOMMENDED -> Color(0xFFEF6C00)
    ContextHealthStatus.HIGH_RISK -> Color(0xFFD32F2F)
}

private fun formatCharacters(value: Long): String =
    if (value >= 10_000) "%.1f万字符".format(value / 10_000.0) else value.toString() + " 字符"
