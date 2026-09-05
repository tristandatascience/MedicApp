package com.medicapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medicapp.ui.dashboard.DashboardScreen
import com.medicapp.ui.modules.appointments.AppointmentDetailScreen
import com.medicapp.ui.modules.appointments.AppointmentFormScreen
import com.medicapp.ui.modules.appointments.AppointmentsScreen
import com.medicapp.ui.modules.exams.ExamDetailScreen
import com.medicapp.ui.modules.exams.ExamFormScreen
import com.medicapp.ui.modules.exams.ExamsScreen
import com.medicapp.ui.modules.prescriptions.PrescriptionDetailScreen
import com.medicapp.ui.modules.prescriptions.PrescriptionFormScreen
import com.medicapp.ui.modules.prescriptions.PrescriptionsScreen
import com.medicapp.ui.modules.treatments.TreatmentDetailScreen
import com.medicapp.ui.modules.treatments.TreatmentFormScreen
import com.medicapp.ui.modules.treatments.TreatmentsScreen
import com.medicapp.ui.modules.vaccinations.VaccinationDetailScreen
import com.medicapp.ui.modules.vaccinations.VaccinationFormScreen
import com.medicapp.ui.modules.vaccinations.VaccinationsScreen
import com.medicapp.ui.profiles.ProfilesScreen
import com.medicapp.ui.search.SearchScreen
import com.medicapp.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val VACCINATIONS = "vaccinations"
    const val TREATMENTS = "treatments"
    const val PRESCRIPTIONS = "prescriptions"
    const val EXAMS = "exams"
    const val APPOINTMENTS = "appointments"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PROFILES = "profiles"

    const val VACCINATION_DETAIL = "vaccination/{id}"
    const val VACCINATION_FORM = "vaccination-form/{id}?doc={doc}"
    const val TREATMENT_DETAIL = "treatment/{id}"
    const val TREATMENT_FORM = "treatment-form/{id}?doc={doc}"
    const val PRESCRIPTION_DETAIL = "prescription/{id}"
    const val PRESCRIPTION_FORM = "prescription-form/{id}?doc={doc}"
    const val EXAM_DETAIL = "exam/{id}"
    const val EXAM_FORM = "exam-form/{id}?doc={doc}"
    const val APPOINTMENT_DETAIL = "appointment/{id}"
    const val APPOINTMENT_FORM = "appointment-form/{id}?doc={doc}"
    const val SCAN = "scan/{ownerType}/{ownerId}"
    const val DOCUMENT = "document/{id}"
    const val DOCUMENTS = "documents"

    fun vaccinationDetail(id: Long) = "vaccination/$id"
    fun vaccinationForm(id: Long, doc: Long? = null) = formUrl("vaccination-form/$id", doc)
    fun treatmentDetail(id: Long) = "treatment/$id"
    fun treatmentForm(id: Long, doc: Long? = null) = formUrl("treatment-form/$id", doc)
    fun prescriptionDetail(id: Long) = "prescription/$id"
    fun prescriptionForm(id: Long, doc: Long? = null) = formUrl("prescription-form/$id", doc)
    fun examDetail(id: Long) = "exam/$id"
    fun examForm(id: Long, doc: Long? = null) = formUrl("exam-form/$id", doc)
    fun appointmentDetail(id: Long) = "appointment/$id"
    fun appointmentForm(id: Long, doc: Long? = null) = formUrl("appointment-form/$id", doc)
    fun scan(owner: com.medicapp.data.db.entity.DocumentOwner, ownerId: Long?): String =
        "scan/${owner.name}/${ownerId ?: -1L}"
    fun document(id: Long) = "document/$id"

    /** Route de création depuis un document scanné (fiche pré-remplie par OCR). */
    fun formForScan(owner: com.medicapp.data.db.entity.DocumentOwner, docId: Long): String? =
        when (owner) {
            com.medicapp.data.db.entity.DocumentOwner.VACCINATION -> vaccinationForm(-1L, docId)
            com.medicapp.data.db.entity.DocumentOwner.TREATMENT -> treatmentForm(-1L, docId)
            com.medicapp.data.db.entity.DocumentOwner.PRESCRIPTION -> prescriptionForm(-1L, docId)
            com.medicapp.data.db.entity.DocumentOwner.EXAM -> examForm(-1L, docId)
            com.medicapp.data.db.entity.DocumentOwner.APPOINTMENT -> appointmentForm(-1L, docId)
            com.medicapp.data.db.entity.DocumentOwner.STANDALONE -> null
        }

    private fun formUrl(base: String, doc: Long?): String =
        if (doc != null && doc > 0) "$base?doc=$doc" else base
}

data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Barre inférieure : accueil + les 5 modules (§ 8 du cahier des charges). */
val topDestinations = listOf(
    TopDestination(Routes.HOME, "Accueil", Icons.Outlined.Home),
    TopDestination(Routes.VACCINATIONS, "Vaccins", Icons.Outlined.Vaccines),
    TopDestination(Routes.TREATMENTS, "Traitements", Icons.Outlined.Medication),
    TopDestination(Routes.PRESCRIPTIONS, "Ordonn.", Icons.Outlined.Description),
    TopDestination(Routes.EXAMS, "Examens", Icons.Outlined.Science),
    TopDestination(Routes.APPOINTMENTS, "RDV", Icons.Outlined.Event),
)

private val idArgument = listOf(navArgument("id") { type = NavType.LongType })

private val formArguments = listOf(
    navArgument("id") { type = NavType.LongType },
    navArgument("doc") {
        type = NavType.LongType
        defaultValue = -1L
    },
)

@Composable
fun MedicNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topDestinations.map { it.route }

    val navigateUp: () -> Unit = { navController.popBackStack() }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                DashboardScreen(
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDocuments = { navController.navigate(Routes.DOCUMENTS) },
                    onOpenModule = { route -> navController.navigate(route) },
                    onManageProfiles = { navController.navigate(Routes.PROFILES) },
                )
            }

            // --- Vaccinations ---
            composable(Routes.VACCINATIONS) {
                VaccinationsScreen(
                    onOpenDetail = { navController.navigate(Routes.vaccinationDetail(it)) },
                    onOpenForm = { navController.navigate(Routes.vaccinationForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.VACCINATION, null)) },
                )
            }
            composable(Routes.VACCINATION_DETAIL, arguments = idArgument) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                VaccinationDetailScreen(
                    id = id,
                    onBack = navigateUp,
                    onEdit = { navController.navigate(Routes.vaccinationForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.VACCINATION, id)) },
                    onOpenDocument = { navController.navigate(Routes.document(it)) },
                )
            }
            composable(Routes.VACCINATION_FORM, arguments = formArguments) { entry ->
                VaccinationFormScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    scanDocumentId = entry.arguments?.getLong("doc")?.takeIf { it > 0 },
                    onBack = navigateUp,
                )
            }

            // --- Traitements ---
            composable(Routes.TREATMENTS) {
                TreatmentsScreen(
                    onOpenDetail = { navController.navigate(Routes.treatmentDetail(it)) },
                    onOpenForm = { navController.navigate(Routes.treatmentForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.TREATMENT, null)) },
                )
            }
            composable(Routes.TREATMENT_DETAIL, arguments = idArgument) { entry ->
                TreatmentDetailScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onBack = navigateUp,
                    onEdit = { navController.navigate(Routes.treatmentForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.TREATMENT, entry.arguments?.getLong("id"))) },
                    onOpenDocument = { navController.navigate(Routes.document(it)) },
                )
            }
            composable(Routes.TREATMENT_FORM, arguments = formArguments) { entry ->
                TreatmentFormScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    scanDocumentId = entry.arguments?.getLong("doc")?.takeIf { it > 0 },
                    onBack = navigateUp,
                )
            }

            // --- Ordonnances ---
            composable(Routes.PRESCRIPTIONS) {
                PrescriptionsScreen(
                    onOpenDetail = { navController.navigate(Routes.prescriptionDetail(it)) },
                    onOpenForm = { navController.navigate(Routes.prescriptionForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.PRESCRIPTION, null)) },
                )
            }
            composable(Routes.PRESCRIPTION_DETAIL, arguments = idArgument) { entry ->
                PrescriptionDetailScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onBack = navigateUp,
                    onEdit = { navController.navigate(Routes.prescriptionForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.PRESCRIPTION, entry.arguments?.getLong("id"))) },
                    onOpenDocument = { navController.navigate(Routes.document(it)) },
                )
            }
            composable(Routes.PRESCRIPTION_FORM, arguments = formArguments) { entry ->
                PrescriptionFormScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    scanDocumentId = entry.arguments?.getLong("doc")?.takeIf { it > 0 },
                    onBack = navigateUp,
                )
            }

            // --- Examens ---
            composable(Routes.EXAMS) {
                ExamsScreen(
                    onOpenDetail = { navController.navigate(Routes.examDetail(it)) },
                    onOpenForm = { navController.navigate(Routes.examForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.EXAM, null)) },
                )
            }
            composable(Routes.EXAM_DETAIL, arguments = idArgument) { entry ->
                ExamDetailScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onBack = navigateUp,
                    onEdit = { navController.navigate(Routes.examForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.EXAM, entry.arguments?.getLong("id"))) },
                    onOpenDocument = { navController.navigate(Routes.document(it)) },
                )
            }
            composable(Routes.EXAM_FORM, arguments = formArguments) { entry ->
                ExamFormScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    scanDocumentId = entry.arguments?.getLong("doc")?.takeIf { it > 0 },
                    onBack = navigateUp,
                )
            }

            // --- Rendez-vous ---
            composable(Routes.APPOINTMENTS) {
                AppointmentsScreen(
                    onOpenDetail = { navController.navigate(Routes.appointmentDetail(it)) },
                    onOpenForm = { navController.navigate(Routes.appointmentForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.APPOINTMENT, null)) },
                )
            }
            composable(Routes.APPOINTMENT_DETAIL, arguments = idArgument) { entry ->
                AppointmentDetailScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onBack = navigateUp,
                    onEdit = { navController.navigate(Routes.appointmentForm(it)) },
                    onScan = { navController.navigate(Routes.scan(com.medicapp.data.db.entity.DocumentOwner.APPOINTMENT, entry.arguments?.getLong("id"))) },
                    onOpenDocument = { navController.navigate(Routes.document(it)) },
                )
            }
            composable(Routes.APPOINTMENT_FORM, arguments = formArguments) { entry ->
                AppointmentFormScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    scanDocumentId = entry.arguments?.getLong("doc")?.takeIf { it > 0 },
                    onBack = navigateUp,
                )
            }

            composable(Routes.SEARCH) { SearchScreen(onBack = navigateUp) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = navigateUp) }
            composable(Routes.PROFILES) { ProfilesScreen() }

            composable(
                Routes.SCAN,
                arguments = listOf(
                    navArgument("ownerType") { type = NavType.StringType },
                    navArgument("ownerId") { type = NavType.LongType },
                ),
            ) { entry ->
                val owner = com.medicapp.data.db.entity.DocumentOwner.entries.firstOrNull {
                    it.name == entry.arguments?.getString("ownerType")
                } ?: com.medicapp.data.db.entity.DocumentOwner.STANDALONE
                val ownerId = entry.arguments?.getLong("ownerId")?.takeIf { it > 0 }
                com.medicapp.scan.ScanFlowScreen(
                    owner = owner,
                    ownerId = ownerId,
                    onDone = { documentId ->
                        navController.popBackStack()
                        // Scan sans fiche d'origine : ouvrir le formulaire du module
                        // pré-rempli à partir du texte OCR (validation manuelle).
                        if (ownerId == null) {
                            Routes.formForScan(owner, documentId)?.let { formRoute ->
                                navController.navigate(formRoute)
                                return@ScanFlowScreen
                            }
                        }
                        navController.navigate(Routes.document(documentId))
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(Routes.DOCUMENT, arguments = idArgument) { entry ->
                com.medicapp.ui.documents.DocumentViewerScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onBack = navigateUp,
                )
            }
            composable(Routes.DOCUMENTS) {
                com.medicapp.ui.documents.DocumentsScreen(
                    onBack = navigateUp,
                    onOpenDocument = { navController.navigate(Routes.document(it)) },
                )
            }
        }
    }
}
