package com.example.confe


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.confe.UserIdentifier
import java.util.UUID

class BleScanner(
    private val context: Context,
    private val onDeviceDetected: (UserIdentifier, Int, Long) -> Unit
) {

    companion object {
        private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private const val SCAN_DURATION_MS = 30000L // 30 segundos
        private const val TAG = "BleScanner"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var isScanning = false
    private val detectedDevices = mutableMapOf<String, DeviceDetection>()

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth no está habilitado")
            return
        }

        if (isScanning) {
            Log.w(TAG, "Ya está escaneando")
            return
        }

        Log.d(TAG, "Iniciando escaneo BLE...")

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
        Log.d(TAG, "Escaneo iniciado exitosamente")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        if (!isScanning) return

        Log.d(TAG, "Deteniendo escaneo BLE...")
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false

        processDetectedDevices()
        detectedDevices.clear()
        Log.d(TAG, "Escaneo detenido")
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun isCurrentlyScanning(): Boolean {
        return isScanning
    }

    private fun handleScanResult(result: ScanResult) {
        val deviceAddress = result.device.address
        val rssi = result.rssi
        val currentTime = System.currentTimeMillis()

        val identifier = extractIdentifierFromScanResult(result)
        if (identifier == null) {
            Log.w(TAG, "No se pudo extraer identificador del dispositivo: $deviceAddress")
            return
        }

        Log.d(TAG, "Identificador extraído: ${identifier.id} de dispositivo: $deviceAddress")

        val existingDetection = detectedDevices[deviceAddress]
        if (existingDetection != null) {
            existingDetection.lastSeen = currentTime
            existingDetection.rssiReadings.add(rssi)
            if (existingDetection.rssiReadings.size > 10) {
                existingDetection.rssiReadings.removeAt(0)
            }
        } else {
            detectedDevices[deviceAddress] = DeviceDetection(
                identifier = identifier,
                firstSeen = currentTime,
                lastSeen = currentTime,
                rssiReadings = mutableListOf(rssi)
            )
        }
    }

    private fun extractIdentifierFromScanResult(result: ScanResult): UserIdentifier? {
        return try {
            val scanRecord = result.scanRecord
            val serviceData = scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))

            if (serviceData != null && serviceData.isNotEmpty()) {
                Log.d(TAG, "Service data encontrado, tamaño: ${serviceData.size}")
                UserIdentifier.fromBytes(serviceData)
            } else {
                Log.w(TAG, "No se encontró service data")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo identificador: ${e.message}")
            null
        }
    }

    private fun processDetectedDevices() {
        Log.d(TAG, "Procesando ${detectedDevices.size} dispositivos detectados")
        detectedDevices.values.forEach { detection ->
            val averageRssi = detection.rssiReadings.average().toInt()
            onDeviceDetected(detection.identifier, averageRssi, detection.firstSeen)
            Log.d(TAG, "Dispositivo procesado: ${detection.identifier.id}, RSSI promedio: $averageRssi")
        }
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    private fun scheduleStopScanning() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(@androidx.annotation.RequiresPermission(
            android.Manifest.permission.BLUETOOTH_SCAN
        ) {  stopScanning()
        }, SCAN_DURATION_MS)
    }
}

private data class DeviceDetection(
    val identifier: UserIdentifier,
    val firstSeen: Long,
    var lastSeen: Long,
    val rssiReadings: MutableList<Int>
)