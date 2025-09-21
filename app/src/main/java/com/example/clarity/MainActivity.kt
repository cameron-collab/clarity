package com.example.clarity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.example.clarity.ui.AppNav
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.log.LogLevel
import com.example.clarity.api.payment.BackendConnectionTokenProvider
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.PaymentStatus

class MainActivity : ComponentActivity() {

    // Register the permissions callback to handle the response to the system permissions dialog
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onPermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { Surface { AppNav() } }
        }
    }

    override fun onResume() {
        super.onResume()
        requestPermissionsIfNecessary()
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNecessary() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissionsIfNecessarySdk31()
        } else {
            requestPermissionsIfNecessarySdkBelow31()
        }
    }

    private fun requestPermissionsIfNecessarySdkBelow31() {
        // Check for location permissions
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // If we don't have them yet, request them before doing anything else
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else if (!Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestPermissionsIfNecessarySdk31() {
        // Check for location and bluetooth permissions
        val deniedPermissions = mutableListOf<String>().apply {
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!isGranted(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!isGranted(Manifest.permission.BLUETOOTH_SCAN)) add(Manifest.permission.BLUETOOTH_SCAN)
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            // If we don't have them yet, request them before doing anything else
            requestPermissionLauncher.launch(deniedPermissions)
        } else if (!Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    /**
     * Receive the result of our permissions check, and initialize if we can
     */
    private fun onPermissionResult(result: Map<String, Boolean>) {
        val deniedPermissions: List<String> = result
            .filter { !it.value }
            .map { it.key }

        // If we receive a response to our permission check, initialize
        if (deniedPermissions.isEmpty() && !Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    private fun initialize() {
        // Initialize the Terminal as soon as possible
        try {
            Terminal.initTerminal(
                applicationContext,
                LogLevel.VERBOSE,
                BackendConnectionTokenProvider(),
                object : TerminalListener {
                    override fun onConnectionStatusChange(status: ConnectionStatus) {
                        // Handle connection status changes
                    }

                    override fun onPaymentStatusChange(status: PaymentStatus) {
                        // Handle payment status changes
                    }
                }
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Location services are required to initialize the Terminal.",
                e
            )
        }
    }

    private fun verifyGpsEnabled(): Boolean {
        val locationManager: LocationManager? =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        var gpsEnabled = false

        try {
            gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (exception: Exception) {}

        if (!gpsEnabled) {
            // notify user
            AlertDialog.Builder(this)
                .setMessage("Please enable location services")
                .setCancelable(false)
                .setPositiveButton("Open location settings") { _, _ ->
                    this.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .create()
                .show()
        }

        return gpsEnabled
    }
}