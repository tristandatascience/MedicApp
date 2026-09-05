package com.medicapp.data.repo

import com.medicapp.data.db.dao.DocumentDao
import com.medicapp.data.db.entity.DocumentEntity
import com.medicapp.data.db.entity.DocumentOwner
import com.medicapp.data.storage.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class DocumentRepository(
    private val dao: DocumentDao,
    private val store: DocumentStore,
) {
    fun observeForProfile(profileId: Long): Flow<List<DocumentEntity>> = dao.observeForProfile(profileId)

    fun observeForOwner(owner: DocumentOwner, ownerId: Long): Flow<List<DocumentEntity>> =
        dao.observeForOwner(owner, ownerId)

    fun observeById(id: Long): Flow<DocumentEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): DocumentEntity? = dao.getById(id)

    /** Enregistre un document chiffré et retourne son identifiant. */
    suspend fun create(
        profileId: Long,
        title: String,
        mimeType: String,
        pageCount: Int,
        bytes: ByteArray,
        owner: DocumentOwner,
        ownerId: Long?,
        ocrText: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val storageKey = store.save(bytes)
        dao.insert(
            DocumentEntity(
                profileId = profileId,
                title = title,
                mimeType = mimeType,
                pageCount = pageCount,
                storageKey = storageKey,
                ocrText = ocrText,
                ownerType = owner,
                ownerId = ownerId,
            )
        )
    }

    /** Remplace le fichier d'un document existant (nouvelle numérisation). */
    suspend fun replaceFile(id: Long, bytes: ByteArray, mimeType: String, pageCount: Int) =
        withContext(Dispatchers.IO) {
            val doc = dao.getById(id) ?: return@withContext
            val newKey = store.save(bytes)
            dao.update(
                doc.copy(
                    storageKey = newKey,
                    mimeType = mimeType,
                    pageCount = pageCount,
                    updatedAt = LocalDateTime.now(),
                )
            )
            store.delete(doc.storageKey)
        }

    suspend fun updateOcr(id: Long, ocrText: String?) =
        dao.updateOcr(id, ocrText, System.currentTimeMillis())

    suspend fun updateTitle(id: Long, title: String) =
        dao.updateTitle(id, title.trim(), System.currentTimeMillis())

    suspend fun open(storageKey: String): ByteArray = withContext(Dispatchers.IO) { store.open(storageKey) }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { doc ->
            dao.delete(id)
            store.delete(doc.storageKey)
        }
    }

    suspend fun deleteForOwner(owner: DocumentOwner, ownerId: Long) = withContext(Dispatchers.IO) {
        dao.getAll().filter { it.ownerType == owner && it.ownerId == ownerId }.forEach { doc ->
            dao.delete(doc.id)
            store.delete(doc.storageKey)
        }
    }
}
