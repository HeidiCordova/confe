package com.example.confe

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.confe.BleViewModel
import com.example.confe.DetectedDevice
import java.text.SimpleDateFormat
import java.util.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.confe.ui.theme.ConfeTheme

class MainActivity : ComponentActivity() {
/*    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConfeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
*/

    companion object {
        private const val TAG = "MainActivity"
    }

    private val bleViewModel: BleViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permisos resultado: $permissions")
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "Todos los permisos concedidos")
        } else {
            Log.w(TAG, "Algunos permisos fueron denegados")
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Resultado de habilitar Bluetooth: ${result.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity creada")

        checkAndRequestPermissions()

        setContent {
            ConfeTheme {
                BleContactTracingApp(
                    viewModel = bleViewModel,
                    onEnableBluetoothRequest = { enableBluetooth() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Permisos de ubicación (necesarios para BLE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Permisos específicos de Bluetooth para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.d(TAG, "Solicitando permisos: $permissions")
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            Log.d(TAG, "Todos los permisos ya concedidos")
        }
    }

    private fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleContactTracingApp(
    viewModel: BleViewModel,
    onEnableBluetoothRequest: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val detectedDevices by viewModel.detectedDevices.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título
        Text(
            text = "BLE Contact Tracing",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Información del identificador actual
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Identificador Actual:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = uiState.currentIdentifier?.id?.substring(0, 8) + "..." ?: "Cargando...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Button(
                    onClick = { viewModel.rotateIdentifier() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rotar Identificador")
                }
            }
        }

        // Controles de Advertising
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Transmisión BLE",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.startAdvertising() },
                        enabled = !uiState.isAdvertising
                    ) {
                        Text("Iniciar Transmisión")
                    }

                    Button(
                        onClick = { viewModel.stopAdvertising() },
                        enabled = uiState.isAdvertising
                    ) {
                        Text("Detener Transmisión")
                    }
                }

                if (uiState.isAdvertising) {
                    Text(
                        text = "🟢 Transmitiendo...",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Controles de Scanning
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Escaneo BLE",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.startScanning() },
                        enabled = !uiState.isScanning
                    ) {
                        Text("Iniciar Escaneo")
                    }

                    Button(
                        onClick = { viewModel.stopScanning() },
                        enabled = uiState.isScanning
                    ) {
                        Text("Detener Escaneo")
                    }
                }

                if (uiState.isScanning) {
                    Text(
                        text = "🔍 Escaneando...",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Lista de dispositivos detectados
        if (detectedDevices.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dispositivos Detectados (${detectedDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(detectedDevices) { device ->
                            DeviceItem(device = device)
                        }
                    }
                }
            }
        }

        // Mostrar errores
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: DetectedDevice) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ID: ${device.identifier.id.substring(0, 8)}...",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RSSI: ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = timeFormat.format(Date(device.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }



}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ConfeTheme {
        Greeting("Android")
    }
}