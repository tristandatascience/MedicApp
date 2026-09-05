package com.medicapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Pavé numérique pour la saisie du code PIN. */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> PinKey(digit, onDigit, enabled) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp))
            PinKey('0', onDigit, enabled)
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Effacer",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun PinKey(digit: Char, onDigit: (Char) -> Unit, enabled: Boolean) {
    Surface(
        onClick = { onDigit(digit) },
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** Points de saisie du PIN (un par chiffre saisi). */
@Composable
fun PinDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { index ->
            val filledDot = index < filled
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (filledDot) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
            )
        }
    }
}
