package com.example.confe

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.confe.UserIdentifier

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import java.util.UUID

@SuppressLint("MissingPermission")
class BleAdvertiser(
    private val context: Context,
    private val identifierManager: IdentifierManager
) {

    companion object {
        private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        private const val ADVERTISE_DURATION_MS = 30000L
        private const val TAG = "BleAdvertiser"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.d(TAG, "✅ Advertising iniciado exitosamente")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos demasiado grandes"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados advertisers activos"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising ya iniciado"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Característica no soportada"
                else -> "Error desconocido: $errorCode"
            }
            Log.e(TAG, "❌ Advertising falló: $errorMessage (código: $errorCode)")
        }
    }

    // Función para convertir bytes a string hexadecimal legible
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { byte -> "%02X".format(byte) }
    }

    // Función para mostrar la estructura completa del advertising packet
    private fun logAdvertisingPacketStructure(identifierBytes: ByteArray) {
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        Log.d(TAG, "📦 ESTRUCTURA DEL ADVERTISING PACKET")
        Log.d(TAG, "═══════════════════════════════════════════════════════")

        // 1. Información del Service UUID
        Log.d(TAG, "🔵 SERVICE UUID:")
        Log.d(TAG, "   └── UUID Completo: $SERVICE_UUID")
        Log.d(TAG, "   └── UUID Comprimido (16-bit): 180F")
        Log.d(TAG, "   └── Bytes en advertising: [0F 18] (2 bytes)")

        // 2. Información de los datos del identificador
        Log.d(TAG, "🆔 IDENTIFIER DATA:")
        Log.d(TAG, "   └── Tamaño: ${identifierBytes.size} bytes")
        Log.d(TAG, "   └── Contenido (HEX): ${bytesToHex(identifierBytes)}")
        Log.d(TAG, "   └── Contenido (String): ${String(identifierBytes, Charsets.UTF_8).take(20)}...")

        // 3. Cálculo estimado del tamaño total
        val flagsSize = 3  // Flags standard BLE
        val uuidSize = 4   // 2 bytes UUID + 2 bytes header
        val serviceDataHeaderSize = 2  // Header para service data
        val serviceDataSize = identifierBytes.size
        val totalEstimated = flagsSize + uuidSize + serviceDataHeaderSize + serviceDataSize

        Log.d(TAG, "📊 CÁLCULO DE TAMAÑO:")
        Log.d(TAG, "   ├── Flags: $flagsSize bytes")
        Log.d(TAG, "   ├── Service UUID: $uuidSize bytes (2 + 2 header)")
        Log.d(TAG, "   ├── Service Data Header: $serviceDataHeaderSize bytes")
        Log.d(TAG, "   ├── Service Data Payload: $serviceDataSize bytes")
        Log.d(TAG, "   └── TOTAL ESTIMADO: $totalEstimated bytes (máximo: 31 bytes)")

        if (totalEstimated > 31) {
            Log.w(TAG, "⚠️  ADVERTENCIA: Tamaño excede el límite de 31 bytes!")
        } else {
            Log.d(TAG, "✅ Tamaño dentro del límite permitido")
        }

        // 4. Lo que vería un scanner
        Log.d(TAG, "🔍 LO QUE VERÍA UN SCANNER:")
        Log.d(TAG, "   ├── Device Address: XX:XX:XX:XX:XX:XX (MAC address)")
        Log.d(TAG, "   ├── RSSI: -XX dBm (señal de fuerza)")
        Log.d(TAG, "   ├── Service UUID: 0000180F-0000-1000-8000-00805F9B34FB")
        Log.d(TAG, "   └── Service Data: ${bytesToHex(identifierBytes)}")

        Log.d(TAG, "═══════════════════════════════════════════════════════")
    }

    // FUNCIÓN PARA VERIFICAR PERMISOS
    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // En versiones anteriores a Android 12, no se necesita este permiso específico
        }
    }

    fun startAdvertising() {
        // VERIFICAR PERMISOS ANTES DE CONTINUAR
        if (!hasAdvertisePermission()) {
            Log.e(TAG, "❌ Permiso BLUETOOTH_ADVERTISE no concedido")
            return
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth no está habilitado o no soporta advertising")
            return
        }

        if (isAdvertising) {
            Log.w(TAG, "Ya está transmitiendo")
            return
        }

        Log.d(TAG, "🚀 Iniciando advertising BLE...")

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(ADVERTISE_DURATION_MS.toInt())
                .build()

            val identifierBytes = UserIdentifier.toBytes()

            // LOG DETALLADO DE LA ESTRUCTURA DEL PACKET
            logAdvertisingPacketStructure(identifierBytes)

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .addServiceData(ParcelUuid(SERVICE_UUID), identifierBytes)
                .build()

            Log.d(TAG, "📡 Iniciando transmisión con configuración:")
            Log.d(TAG, "   ├── Modo: LOW_POWER")
            Log.d(TAG, "   ├── Potencia TX: LOW")
            Log.d(TAG, "   ├── Conectable: NO")
            Log.d(TAG, "   └── Duración: ${ADVERTISE_DURATION_MS/1000} segundos")

            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            scheduleStopAdvertising()

        } catch (e: SecurityException) {
            Log.e(TAG, "💥 SecurityException: Permisos insuficientes - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error al iniciar advertising: ${e.message}")
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) return

        // VERIFICAR PERMISOS ANTES DE DETENER
        if (!hasAdvertisePermission()) {
            Log.e(TAG, "❌ Permiso BLUETOOTH_ADVERTISE no concedido para detener")
            return
        }

        Log.d(TAG, "🛑 Deteniendo advertising BLE...")
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "✅ Advertising detenido")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al detener: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo advertising: ${e.message}")
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val enabled = bluetoothAdapter?.isEnabled == true &&
                bluetoothLeAdvertiser != null &&
                bluetoothAdapter.isMultipleAdvertisementSupported

        Log.d(TAG, "🔵 Bluetooth habilitado: $enabled")
        return enabled
    }

    fun isCurrentlyAdvertising(): Boolean {
        return isAdvertising
    }

    fun rotateAndRestartAdvertising() {
        Log.d(TAG, "🔄 Rotando identificador y reiniciando advertising...")
        val wasAdvertising = isAdvertising
        if (wasAdvertising) {
            stopAdvertising()
        }

        identifierManager.rotateIdentifier()

        if (wasAdvertising) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startAdvertising()
            }, 100)
        }
    }

    private fun scheduleStopAdvertising() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopAdvertising()
        }, ADVERTISE_DURATION_MS)
    }
}

// ================================================================
// SOLUCIÓN 2: BleScanner con verificación de permisos
// ================================================================

/*
class BleScanner(
    private val context: Context,
    private val onDeviceDetected: (UserIdentifier, Int, Long) -> Unit
) {

    companion object {
        private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private const val SCAN_DURATION_MS = 30000L
        private const val TAG = "BleScanner"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var isScanning = false
    private val detectedDevices = mutableMapOf<String, DeviceDetection>()

    // FUNCIÓN PARA VERIFICAR PERMISOS DE ESCANEO
    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "Dispositivo detectado: ${result.device.address}, RSSI: ${result.rssi}")
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d(TAG, "Batch scan results: ${results.size} dispositivos")
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e(TAG, "Scan falló con código: $errorCode")
        }
    }

    fun startScanning() {
        // VERIFICAR PERMISOS ANTES DE ESCANEAR
        if (!hasScanPermission()) {
            Log.e(TAG, "❌ Permisos de BLUETOOTH_SCAN no concedidos")
            return
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth no está habilitado")
            return
        }

        if (isScanning) {
            Log.w(TAG, "Ya está escaneando")
            return
        }

        Log.d(TAG, "🔍 Iniciando escaneo BLE...")

        try {
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0L)
                .build()

            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            scheduleStopScanning()
            Log.d(TAG, "✅ Escaneo iniciado exitosamente")

        } catch (e: SecurityException) {
            Log.e(TAG, "💥 SecurityException: Permisos insuficientes - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error al iniciar escaneo: ${e.message}")
        }
    }

    fun stopScanning() {
        if (!isScanning) return

        if (!hasScanPermission()) {
            Log.e(TAG, "❌ Permisos de BLUETOOTH_SCAN no concedidos para detener")
            return
        }

        Log.d(TAG, "🛑 Deteniendo escaneo BLE...")
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            processDetectedDevices()
            detectedDevices.clear()
            Log.d(TAG, "✅ Escaneo detenido")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al detener escaneo: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo escaneo: ${e.message}")
        }
    }

    // ... resto del código igual
}
*/

// ================================================================
// SOLUCIÓN 3: Alternativa simple - usar @SuppressLint
// ================================================================

/*
import android.annotation.SuppressLint

@SuppressLint("MissingPermission")
class BleAdvertiser(
    private val context: Context,
    private val identifierManager: IdentifierManager
) {
    // Todo tu código actual sin cambios
    // Esta anotación suprime las advertencias de permisos
}
*/