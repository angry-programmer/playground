package com.example.playground

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.*
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_main.*
import java.util.regex.Pattern


class MainActivity : AppCompatActivity() {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var unregisterNetworkCallback: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            oldAndroidTextView.isVisible = true
            return
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        requestLocation()

        connectButton.setOnClickListener {
            if (!wifiManager.isWifiEnabled) {
                showToast("Please enable your WIFI!")
                val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                startActivity(panelIntent)
                return@setOnClickListener
            }
            val ssid = hotspotSSIDEditText.text.toString()
            val mac = hotspotBSSIDEditText.text.toString()
            val password = hotspotPasswordEditText.text.toString()
            validate(ssid, mac, password)
        }

        clearConsoleButton.setOnClickListener {
            logTextView.text = ""
        }

        copyMacButton.setOnClickListener {
            copyMac()
        }

        unregisterNetworkCallbackSwitch.setOnCheckedChangeListener { _, unregister ->
            unregisterNetworkCallback = unregister
        }
    }

    private fun requestLocation() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            4
        )
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        if (!wifiManager.isWifiEnabled) {
            showToast("Please enable your WIFI!")
            val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            startActivity(panelIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun validate(ssid: String, mac: String, password: String) {
        if (ssid.isBlank()) {
            showToast("SSID is empty!")
            return
        }
        if (mac.isBlank()) {
            showToast("MAC is empty!")
            return
        }
        if (!isMacValid(mac)) {
            showToast("Invalid MAC!")
            return
        }
        val networkSpecifier = if (password.isBlank()) {
            getNetworkSpecifier(ssid, mac)
        } else {
            getNetworkSpecifierWithPassword(ssid, mac, password)
        }
        switch(networkSpecifier)
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun isMacValid(mac: String): Boolean {
        val pattern = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return pattern.matcher(mac).find()
    }

    private fun getNetworkSpecifier(ssid: String, mac: String): WifiNetworkSpecifier =
        WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setBssid(MacAddress.fromString(mac))
            .build()

    private fun getNetworkSpecifierWithPassword(
        ssid: String,
        mac: String,
        password: String
    ): WifiNetworkSpecifier =
        WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setBssid(MacAddress.fromString(mac))
            .setWpa2Passphrase(password)
            .build()

    @RequiresApi(Build.VERSION_CODES.P)
    private fun switch(wifiNetworkSpecifier: WifiNetworkSpecifier) {
        logTextView.append("Try to switch\n\n")
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                logTextView.append("onAvailable() called with: network = $network\n")
                connectivityManager.bindProcessToNetwork(network)
                if (unregisterNetworkCallback) {
                    connectivityManager.unregisterNetworkCallback(this)
                }
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                super.onBlockedStatusChanged(network, blocked)
                logTextView.append("onBlockedStatusChanged() called with: network = $network\n")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                logTextView.append("onCapabilitiesChanged() called with: network = $network\n")
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                logTextView.append("onLinkPropertiesChanged() called with: network = $network\n")
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                super.onLosing(network, maxMsToLive)
                logTextView.append("onLosing() called with: network = $network\n")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                logTextView.append("onLost() called with: network = $network\n")
                connectivityManager.bindProcessToNetwork(null)
                connectivityManager.unregisterNetworkCallback(this)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                logTextView.append("onUnavailable() called\n")
                if (unregisterNetworkCallback) {
                    connectivityManager.unregisterNetworkCallback(this)
                }
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()
        connectivityManager.requestNetwork(networkRequest, networkCallback, 45000)
    }

    private fun copyMac() {
        val ssid = wifiManager.connectionInfo?.ssid
        if (ssid?.contains("Deeper") == false) {
            showToast("Please connect to Deeper!")
            return
        }
        val mac = wifiManager.connectionInfo?.bssid
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("text", mac)
        clipboardManager.setPrimaryClip(clipData)
        showToast("You mac = $mac was copied to clipboard. Restart the app")
    }
}