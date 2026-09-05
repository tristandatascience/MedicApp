package com.medicapp.data.db

import android.content.Context
import androidx.room.Room
import com.medicapp.data.crypto.MasterKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Base de données Room chiffrée via SQLCipher. La phrase secrète est dérivée
 * de la clé maître (elle-même enveloppée par l'Android Keystore) : le fichier
 * de base est illisible sans le matériel de clés du téléphone.
 */
object DatabaseFactory {

    fun create(context: Context, masterKeyManager: MasterKeyManager): MedicDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(masterKeyManager.databasePassphrase())
        return Room.databaseBuilder(context, MedicDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(factory)
            .build()
    }

    /** Base en clair pour les tests unitaires JVM (Robolectric). */
    fun createInMemory(context: Context): MedicDatabase =
        Room.inMemoryDatabaseBuilder(context, MedicDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    const val DATABASE_NAME = "medic.db"
}
