package com.medicapp.data.db

import androidx.test.core.app.ApplicationProvider
import com.medicapp.data.db.entity.Appointment
import com.medicapp.data.db.entity.Profile
import com.medicapp.data.db.entity.Vaccination
import com.medicapp.data.repo.SearchRepository
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests des DAO sur une base en mémoire (Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoTest {

    private lateinit var db: MedicDatabase

    @Before
    fun setup() {
        db = DatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `suppression d'un profil en cascade`() = runBlocking {
        val profileId = db.profileDao().insert(Profile(name = "Camille"))
        val vaccinationId = db.vaccinationDao().insert(
            Vaccination(
                profileId = profileId,
                vaccineName = "ROR",
                disease = "Rougeole, oreillons, rubéole",
                injectionDate = LocalDate.of(2025, 6, 1),
            )
        )
        db.appointmentDao().insert(
            Appointment(
                profileId = profileId,
                dateTime = LocalDateTime.of(2026, 9, 10, 9, 0),
                professional = "Dr Martin",
            )
        )
        db.profileDao().delete(profileId)
        assertNull(db.vaccinationDao().getById(vaccinationId))
        assertEquals(
            0,
            db.appointmentDao().observeUpcoming(profileId, System.currentTimeMillis()).first().size,
        )
        Unit
    }

    @Test
    fun `recherche insensible à la casse`() = runBlocking {
        val profileId = db.profileDao().insert(Profile(name = "Camille"))
        db.vaccinationDao().insert(
            Vaccination(
                profileId = profileId,
                vaccineName = "Grippaux",
                disease = "Grippe saisonnière",
                injectionDate = LocalDate.now(),
            )
        )
        assertEquals(1, db.vaccinationDao().search(profileId, SearchRepository.escapeLike("gripp")).size)
        assertEquals(0, db.vaccinationDao().search(profileId, SearchRepository.escapeLike("variole")).size)
        Unit
    }

    @Test
    fun `les jokers sont traités littéralement`() = runBlocking {
        val profileId = db.profileDao().insert(Profile(name = "Camille"))
        db.vaccinationDao().insert(
            Vaccination(
                profileId = profileId,
                vaccineName = "Vaccin 100% efficace",
                disease = "Test",
                injectionDate = LocalDate.now(),
            )
        )
        // "%" littéral : trouvé seulement si échappé correctement
        assertEquals(1, db.vaccinationDao().search(profileId, SearchRepository.escapeLike("100%")).size)
        // sans échappement, "%" matcherait tout : 0 résultat attendu car aucun nom ne contient "_x_"
        assertEquals(0, db.vaccinationDao().search(profileId, SearchRepository.escapeLike("_efficace")).size)
        Unit
    }
}
