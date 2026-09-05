package com.medicapp.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicapp.data.repo.SearchDomain
import com.medicapp.data.repo.SearchHit
import com.medicapp.di.AppContainer
import com.medicapp.ui.common.Format
import com.medicapp.ui.common.containerViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class SearchFilters(
    val domains: Set<SearchDomain> = emptySet(),
    val periodMonths: Int? = null,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    val query = MutableStateFlow("")
    val filters = MutableStateFlow(SearchFilters())

    val results: StateFlow<List<SearchHit>> =
        combine(query.debounce(250), container.settings.settings, filters) { q, s, f ->
            Triple(q, s.currentProfileId, f)
        }
            .flatMapLatest { (q, profileId, f) ->
                flow {
                    if (q.isBlank() || profileId == 0L) {
                        emit(emptyList())
                    } else {
                        var hits = container.searchRepository.search(profileId, q)
                        if (f.domains.isNotEmpty()) {
                            hits = hits.filter { it.domain in f.domains }
                        }
                        f.periodMonths?.let { months ->
                            val limit = LocalDate.now().minusMonths(months.toLong())
                            hits = hits.filter { hit -> (hit.date ?: LocalDate.MAX) >= limit }
                        }
                        emit(hits)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleDomain(domain: SearchDomain) {
        val current = filters.value.domains
        filters.value = filters.value.copy(
            domains = if (domain in current) current - domain else current + domain
        )
    }

    fun setPeriod(months: Int?) {
        filters.value = filters.value.copy(periodMonths = months)
    }
}

/** Recherche globale : titres, notes et textes OCR (§ 4.8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onOpenResult: (SearchDomain) -> Unit = {},
) {
    val vm: SearchViewModel = containerViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsState()
    val filters by vm.filters.collectAsState()
    val results by vm.results.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("Rechercher dans tout le dossier…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                SearchDomain.entries.forEach { domain ->
                    FilterChip(
                        selected = domain in filters.domains,
                        onClick = { vm.toggleDomain(domain) },
                        label = { Text(domainLabel(domain)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(null to "Toute période", 3 to "3 derniers mois", 12 to "12 derniers mois").forEach { (months, label) ->
                    FilterChip(
                        selected = filters.periodMonths == months,
                        onClick = { vm.setPeriod(months) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (query.isBlank()) {
                Text(
                    "Saisissez un mot-clé : la recherche couvre les titres, notes et textes OCR " +
                        "de tous les modules du profil actif.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (results.isEmpty()) {
                Text("Aucun résultat.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("${results.size} résultat(s)", style = MaterialTheme.typography.labelMedium)
                LazyColumn {
                    items(results, key = { "${it.domain}-${it.id}" }) { hit ->
                        ListItem(
                            headlineContent = { Text(hit.title) },
                            supportingContent = {
                                Text(listOfNotNull(hit.subtitle, Format.dateShort(hit.date)).joinToString(" — "))
                            },
                            leadingContent = { Text(domainEmoji(hit.domain)) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun domainLabel(domain: SearchDomain): String = when (domain) {
    SearchDomain.VACCINATION -> "Vaccins"
    SearchDomain.TREATMENT -> "Traitements"
    SearchDomain.PRESCRIPTION -> "Ordonnances"
    SearchDomain.EXAM -> "Examens"
    SearchDomain.APPOINTMENT -> "RDV"
    SearchDomain.DOCUMENT -> "Documents"
}

private fun domainEmoji(domain: SearchDomain): String = when (domain) {
    SearchDomain.VACCINATION -> "💉"
    SearchDomain.TREATMENT -> "💊"
    SearchDomain.PRESCRIPTION -> "📋"
    SearchDomain.EXAM -> "🔬"
    SearchDomain.APPOINTMENT -> "📅"
    SearchDomain.DOCUMENT -> "📄"
}
