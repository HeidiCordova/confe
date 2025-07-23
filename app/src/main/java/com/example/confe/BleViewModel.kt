package com.example.confe


import android.Manifest
import android.app.Application
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.confe.IdentifierManager
import com.example.confe.BleAdvertiser
import com.example.confe.BleScanner
import com.example.confe.UserIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
class BleViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BleViewModel"
    }

    private val identifierManager = IdentifierManager(application)
    private val bleScanner = BleScanner(application) { identifier, rssi, timestamp ->
        onDeviceDetected(identifier, rssi, timestamp)
    }
    private val bleAdvertiser = BleAdvertiser(application, identifierManager)

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState

    private val _detectedDevices = MutableStateFlow<List<DetectedDevice>>(emptyList())
    val detectedDevices: StateFlow<List<DetectedDevice>> = _detectedDevices

    init {
        Log.d(TAG, "BleViewModel inicializado")
        updateCurrentIdentifier()
    }

    // SOLUCIÓN 1: Con SuppressLint (necesita el import de arriba)
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        Log.d(TAG, "Iniciando advertising desde ViewModel")
        viewModelScope.launch {
            if (!bleAdvertiser.isBluetoothEnabled()) {
                Log.e(TAG, "Bluetooth no está habilitado para advertising")
                _uiState.value = _uiState.value.copy(
                    error = "Bluetooth no está habilitado o no soporta advertising"
                )
                return@launch
            }

            bleAdvertiser.startAdvertising()
            _uiState.value = _uiState.value.copy(
                isAdvertising = true,
                error = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        Log.d(TAG, "Deteniendo advertising desde ViewModel")
        viewModelScope.launch {
            bleAdvertiser.stopAdvertising()
            _uiState.value = _uiState.value.copy(isAdvertising = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        Log.d(TAG, "Iniciando scanning desde ViewModel")
        viewModelScope.launch {
            if (!bleScanner.isBluetoothEnabled()) {
                Log.e(TAG, "Bluetooth no está habilitado para scanning")
                _uiState.value = _uiState.value.copy(
                    error = "Bluetooth no está habilitado"
                )
                return@launch
            }

            // Limpiar dispositivos detectados anteriormente
            _detectedDevices.value = emptyList()

            bleScanner.startScanning()
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                error = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Log.d(TAG, "Deteniendo scanning desde ViewModel")
        viewModelScope.launch {
            bleScanner.stopScanning()
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun rotateIdentifier() {
        Log.d(TAG, "Rotando identificador desde ViewModel")
        viewModelScope.launch {
            identifierManager.rotateIdentifier()
            updateCurrentIdentifier()

            // Si está transmitiendo, reiniciar con el nuevo identificador
            if (_uiState.value.isAdvertising) {
                bleAdvertiser.rotateAndRestartAdvertising()
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun onDeviceDetected(identifier: UserIdentifier, rssi: Int, timestamp: Long) {
        Log.d(TAG, "Dispositivo detectado en ViewModel: ${identifier.id}, RSSI: $rssi")

        val newDevice = DetectedDevice(
            identifier = identifier,
            rssi = rssi,
            timestamp = timestamp
        )

        val currentDevices = _detectedDevices.value.toMutableList()
        currentDevices.add(newDevice)
        _detectedDevices.value = currentDevices
    }

    private fun updateCurrentIdentifier() {
        val currentId = identifierManager.getCurrentIdentifier()
        _uiState.value = _uiState.value.copy(currentIdentifier = currentId)
        Log.d(TAG, "Identificador actual actualizado: ${currentId.id}")
    }

    @SuppressLint("MissingPermission")
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel siendo limpiado")
        bleScanner.stopScanning()
        bleAdvertiser.stopAdvertising()
    }
}

data class BleUiState(
    val isAdvertising: Boolean = false,
    val isScanning: Boolean = false,
    val currentIdentifier: UserIdentifier? = null,
    val error: String? = null
)

data class DetectedDevice(
    val identifier: UserIdentifier,
    val rssi: Int,
    val timestamp: Long
)
