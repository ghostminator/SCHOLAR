package com.ghostminator.scholar.core

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.tasks.await

class DbHelper {

    private val db: FirebaseFirestore = Firebase.firestore.apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(memoryCacheSettings {})
        }
    }

    suspend fun getNotes(): List<String> {
        val snapshot = db.collection("notes")
            .get(Source.SERVER)
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.getString("text")
                ?: doc.getString("content")
                ?: doc.getString("body")
        }
    }
}
