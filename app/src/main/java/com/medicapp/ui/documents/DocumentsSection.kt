package com.medicapp.ui.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DocumentsSectionViewModel(private val container: AppContainer) : ViewModel() {
    fun observe(owner: DocumentOwner, ownerId: Long) =
        container.documentRepository.observeForOwner(owner, ownerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Section « documents numérisés » des fiches : liste + bouton de numérisation. */
@Composable
fun DocumentsSection(
    owner: DocumentOwner,
    ownerId: Long,
    onScan: () -> Unit,
    onOpenDocument: (Long) -> Unit,
) {
    val vm: DocumentsSectionViewModel = containerViewModel { DocumentsSectionViewModel(it) }
    val documents by remember(owner, ownerId) { vm.observe(owner, ownerId) }.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Documents numérisés",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onScan) {
                Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Numériser")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (documents.isEmpty()) {
            Text(
                "Aucun document. Utilisez « Numériser » pour photographier une page " +
                    "(le texte sera reconnu automatiquement).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            documents.forEach { document ->
                Card(
                    onClick = { onOpenDocument(document.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(document.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${document.pageCount} page(s) — ${Format.date(document.createdAt.toLocalDate())}" +
                                if (document.ocrText != null) " — texte indexé" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
