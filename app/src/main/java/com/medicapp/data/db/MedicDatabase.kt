package com.medicapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medicapp.data.db.dao.AppointmentDao
import com.medicapp.data.db.dao.DocumentDao
import com.medicapp.data.db.dao.ExamDao
import com.medicapp.data.db.dao.PrescriptionDao
import com.medicapp.data.db.dao.ProfileDao
import com.medicapp.data.db.dao.ReminderDao
import com.medicapp.data.db.dao.TreatmentDao
import com.medicapp.data.db.dao.VaccinationDao
import com.medicapp.data.db.entity.Appointment
import com.medicapp.data.db.entity.DocumentEntity
import com.medicapp.data.db.entity.Exam
import com.medicapp.data.db.entity.Prescription
import com.medicapp.data.db.entity.PrescriptionMedicine
import com.medicapp.data.db.entity.Profile
import com.medicapp.data.db.entity.Reminder
import com.medicapp.data.db.entity.Treatment
import com.medicapp.data.db.entity.Vaccination

@Database(
    entities = [
        Profile::class,
        Vaccination::class,
        Treatment::class,
        Prescription::class,
        PrescriptionMedicine::class,
        Exam::class,
        Appointment::class,
        DocumentEntity::class,
        Reminder::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MedicDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun vaccinationDao(): VaccinationDao
    abstract fun treatmentDao(): TreatmentDao
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun examDao(): ExamDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun documentDao(): DocumentDao
    abstract fun reminderDao(): ReminderDao
}
