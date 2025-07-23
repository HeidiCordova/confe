package com.example.confe


import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.confe.UserIdentifier



class IdentifierManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "ble_identifier_prefs"
        private const val KEY_CURRENT_ID = "current_identifier_id"
        private const val KEY_CURRENT_TIMESTAMP = "current_identifier_timestamp"
        private const val TAG = "IdentifierManager"
        private const val ROTATION_INTERVAL_MS = 15 * 60 * 1000L // 15 minutos
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var currentIdentifier: UserIdentifier? = null

    init {
        loadOrCreateIdentifier()
    }

    fun getCurrentIdentifier(): UserIdentifier {
        val current = currentIdentifier
        if (current == null || shouldRotateIdentifier(current)) {
            rotateIdentifier()
        }
        return currentIdentifier!!
    }

    fun rotateIdentifier() {
        Log.d(TAG, "Rotando identificador...")
        val newIdentifier = UserIdentifier.generate()
        currentIdentifier = newIdentifier
        saveIdentifier(newIdentifier)
        Log.d(TAG, "Nuevo identificador generado: ${newIdentifier.id}")
    }

    private fun loadOrCreateIdentifier() {
        val savedId = sharedPrefs.getString(KEY_CURRENT_ID, null)
        val savedTimestamp = sharedPrefs.getLong(KEY_CURRENT_TIMESTAMP, 0L)

        if (savedId != null && savedTimestamp > 0) {
            currentIdentifier = UserIdentifier(savedId, savedTimestamp)
            Log.d(TAG, "Identificador cargado: $savedId")
        } else {
            rotateIdentifier()
        }
    }

    private fun saveIdentifier(identifier: UserIdentifier) {
        sharedPrefs.edit()
            .putString(KEY_CURRENT_ID, identifier.id)
            .putLong(KEY_CURRENT_TIMESTAMP, identifier.timestamp)
            .apply()
    }

    private fun shouldRotateIdentifier(identifier: UserIdentifier): Boolean {
        val elapsed = System.currentTimeMillis() - identifier.timestamp
        return elapsed > ROTATION_INTERVAL_MS
    }
}