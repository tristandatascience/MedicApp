package com.medicapp.data.storage

/** Mode de stockage des données. V1 : LOCAL uniquement ; DRIVE prévu (Option B). */
enum class StorageMode {
    LOCAL,
    DRIVE;

    companion object {
        fun fromName(name: String?): StorageMode =
            entries.firstOrNull { it.name == name } ?: LOCAL
    }
}
