package com.medicapp.data.repo

import com.medicapp.data.db.dao.AppointmentDao
import com.medicapp.data.db.dao.TreatmentDao
import com.medicapp.data.db.dao.VaccinationDao
import com.medicapp.data.db.entity.Appointment
import com.medicapp.data.db.entity.Treatment
import com.medicapp.data.db.entity.Vaccination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

data class PlannedIntake(val treatment: Treatment, val time: LocalTime)

data class DashboardState(
    val nextAppointment: Appointment? = null,
    val todaysIntakes: List<PlannedIntake> = emptyList(),
    val upcomingBoosters: List<Vaccination> = emptyList(),
    val upcomingAppointments: List<Appointment> = emptyList(),
) {
    /** Badge de notification : échéances dans les 7 prochains jours (§ 4.1). */
    val badgeCount: Int get() = upcomingBoosters.size + upcomingAppointments.size + todaysIntakes.size
}

class DashboardRepository(
    private val appointmentDao: AppointmentDao,
    private val treatmentDao: TreatmentDao,
    private val vaccinationDao: VaccinationDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeDashboard(profileId: Long): Flow<DashboardState> {
        val now = LocalDateTime.now(clock)
        val nowMillis = System.currentTimeMillis()
        val today = LocalDate.now(clock)
        val horizon = today.plusDays(HORIZON_DAYS)

        val next = appointmentDao.observeNext(profileId, nowMillis)
        val intakes = treatmentDao.observeActive(profileId, today.toEpochDay())
        val vaccinations = vaccinationDao.observeForProfile(profileId)
        val upcoming = appointmentDao.observeUpcoming(profileId, nowMillis)

        return combine(next, intakes, vaccinations, upcoming) { nextAppt, treatments, allVaccinations, upcomingAppts ->
            DashboardState(
                nextAppointment = nextAppt,
                todaysIntakes = treatments
                    .filter { it.isActive(today) && it.startDate <= today }
                    .flatMap { t -> t.intakeTimes.mapNotNull { time -> parseTime(time)?.let { PlannedIntake(t, it) } } }
                    .sortedBy { it.time },
                upcomingBoosters = allVaccinations.filter {
                    it.nextDueDate != null && !it.nextDueDate!!.isBefore(today) && it.nextDueDate!! <= horizon
                },
                upcomingAppointments = upcomingAppts.filter { it.dateTime.toLocalDate() <= horizon },
            )
        }
    }

    companion object {
        const val HORIZON_DAYS = 7L

        fun parseTime(value: String): LocalTime? = try {
            val parts = value.split(":")
            LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
        } catch (_: Exception) {
            null
        }
    }
}
