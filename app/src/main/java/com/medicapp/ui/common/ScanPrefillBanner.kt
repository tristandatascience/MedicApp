package com.medicapp.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Bandeau affiché quand un formulaire a été pré-rempli depuis un scan OCR. */
@Composable
fun ScanPrefillBanner(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text(
                "Document numérisé joint à cette fiche. Les champs reconnus ont été " +
                    "pré-remplis : vérifiez-les, corrigez-les ou complétez avant d'enregistrer. " +
                    "Le document original reste consultable depuis la fiche.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
