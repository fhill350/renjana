package com.fesu.renjana.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Standard confirmation dialog for destructive actions (stop app, stop instance,
 * remove app, delete data).
 *
 * @param destructive when true the confirm label is rendered in error color.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Cancel",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

/** Convenience wrapper for the very common "stop X?" case. */
@Composable
fun StopConfirmDialog(
    what: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = "Stop $what?",
        message = "$what will be closed. Unsaved state inside the app is lost.",
        confirmLabel = "Stop",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
