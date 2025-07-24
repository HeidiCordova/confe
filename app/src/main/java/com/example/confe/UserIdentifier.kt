package com.example.confe

import java.util.UUID
import java.nio.ByteBuffer
import android.util.Log
import kotlin.random.Random

data class UserIdentifier(
    val id: String,
    val timestamp: Long = System.currentTimeMillis()
) {

    companion object {
        private const val TAG = "UserIdentifier"

        // PROBLEMA SOLUCIONADO: Usar una instancia fija en lugar de generar cada vez
        private var currentIdentifierBytes: ByteArray? = null
        private var currentIdentifierString: String? = null

        fun toBytes(): ByteArray {
            // Si ya tenemos bytes generados, reutilizarlos
            if (currentIdentifierBytes != null) {
                Log.d(TAG, "♻️ Reutilizando bytes existentes: ${currentIdentifierBytes!!.size} bytes")
                return currentIdentifierBytes!!
            }

            // OPCIÓN 1: Ultra compacto - 4 bytes
            val buffer = ByteBuffer.allocate(4)
            val randomInt = Random.nextInt()
            buffer.putInt(randomInt)

            // Guardar para reutilizar
            currentIdentifierBytes = buffer.array()
            currentIdentifierString = "ble-${randomInt.toString(16)}"

            Log.d(TAG, "✅ Nuevos bytes generados: ${currentIdentifierBytes!!.size} bytes")
            Log.d(TAG, "📊 ID: $currentIdentifierString")
            Log.d(TAG, "📊 Hex: ${currentIdentifierBytes!!.joinToString("") { "%02x".format(it) }}")

            return currentIdentifierBytes!!
        }

        fun fromBytes(bytes: ByteArray): UserIdentifier? {
            if (bytes.size < 4) {
                Log.w(TAG, "❌ Bytes insuficientes: ${bytes.size}, necesarios: 4")
                return null
            }

            val buffer = ByteBuffer.wrap(bytes)
            val randomInt = buffer.int

            val id = "ble-${randomInt.toString(16)}"
            val identifier = UserIdentifier(id, System.currentTimeMillis())

            Log.d(TAG, "✅ Identificador decodificado: ${identifier.id}")
            Log.d(TAG, "📊 Bytes recibidos: ${bytes.joinToString("") { "%02x".format(it) }}")

            return identifier
        }

        fun generate(): UserIdentifier {
            // Limpiar el cache para generar uno nuevo
            currentIdentifierBytes = null
            currentIdentifierString = null

            // Generar nuevos bytes
            val bytes = toBytes()
            val identifier = UserIdentifier(currentIdentifierString!!)

            Log.d(TAG, "🆕 Nuevo identificador generado: ${identifier.id}")
            return identifier
        }

        // Función para rotar manualmente el identificador
        fun rotateIdentifier() {
            Log.d(TAG, "🔄 Rotando identificador...")
            currentIdentifierBytes = null
            currentIdentifierString = null

            // La próxima llamada a toBytes() generará uno nuevo
            val newBytes = toBytes()
            Log.d(TAG, "✅ Identificador rotado: $currentIdentifierString")
        }

        // Función para obtener el ID actual sin generar bytes
        fun getCurrentIdString(): String? {
            return currentIdentifierString
        }
    }
}
