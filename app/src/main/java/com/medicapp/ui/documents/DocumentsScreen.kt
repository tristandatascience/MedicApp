package com.medicapp.ui.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.db.entity.DocumentEntity
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.data.repo.SearchRepository
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModel(container: AppContainer) : ViewModel() {
    val documents = container.settings.settings
        .flatMapLatest { container.documentRepository.observeForProfile(it.currentProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Liste de tous les documents numérisés du profil actif — y compris ceux qui
 * ne sont pas encore rattachés à une fiche (fin du scan sans enregistrement
 * de la fiche, par exemple). Accessible depuis l'accueil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit = {},
    onOpenDocument: (Long) -> Unit = {},
) {
    val vm: DocumentsViewModel = containerViewModel { DocumentsViewModel(it) }
    val documents by vm.documents.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents numérisés") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (documents.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Aucun document.\nNumérisez vos papiers depuis un module (icône scanner " +
                        "en haut de chaque écran) ou depuis une fiche.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(documents, key = { it.id }) { document ->
                        DocumentRow(document, onClick = { onOpenDocument(document.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: DocumentEntity, onClick: () -> Unit) {
    val unattached = document.ownerId == null
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (unattached) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(document.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${document.pageCount} page(s) — ${Format.date(document.createdAt.toLocalDate())}" +
                    if (document.ocrText != null) " — texte indexé" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (unattached) {
                    "Non rattaché — enregistrez une fiche depuis ce document ou supprimez-le"
                } else {
                    ownerLabelFor(document.ownerType)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (unattached) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ownerLabelFor(owner: DocumentOwner): String = when (owner) {
    DocumentOwner.VACCINATION -> "Rattaché à une vaccination"
    DocumentOwner.TREATMENT -> "Rattaché à un traitement"
    DocumentOwner.PRESCRIPTION -> "Rattaché à une ordonnance"
    DocumentOwner.EXAM -> "Rattaché à un examen"
    DocumentOwner.APPOINTMENT -> "Rattaché à un rendez-vous"
    DocumentOwner.STANDALONE -> "Document isolé"
}
