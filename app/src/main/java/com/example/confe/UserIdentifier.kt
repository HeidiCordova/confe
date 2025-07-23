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

        fun toBytes(): ByteArray {
            // ULTRA COMPACTO: Solo 4 bytes (32 bits)
            val buffer = ByteBuffer.allocate(4)
            val randomInt = Random.nextInt() // Generar entero aleatorio de 32 bits
            buffer.putInt(randomInt)

            Log.d(TAG, "✅ Bytes generados: ${buffer.array().size} bytes")
            Log.d(TAG, "📊 Datos hex: ${buffer.array().joinToString("") { "%02x".format(it) }}")
            return buffer.array()
        }

        fun fromBytes(bytes: ByteArray): UserIdentifier? {
            if (bytes.size < 4) {
                Log.w(TAG, "❌ Bytes insuficientes: ${bytes.size}, necesarios: 4")
                return null
            }

            val buffer = ByteBuffer.wrap(bytes)
            val randomInt = buffer.int

            // Crear un ID único basado en el entero
            val id = "ble-${randomInt.toString(16)}" // Convertir a hex
            val identifier = UserIdentifier(id, System.currentTimeMillis())

            Log.d(TAG, "✅ Identificador decodificado: ${identifier.id}")
            return identifier
        }

        fun generate(): UserIdentifier {
            val randomInt = Random.nextInt()
            val newId = "ble-${randomInt.toString(16)}"
            Log.d(TAG, "✅ Nuevo identificador generado: $newId")
            return UserIdentifier(newId)
        }
    }
}
