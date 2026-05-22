package com.painhunt.app

import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberIsOnline(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val cm = context.getSystemService(ConnectivityManager::class.java)
    var isOnline by remember { mutableStateOf(cm.activeNetwork != null) }

    DisposableEffect(Unit) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    return Pair(isOnline) { isOnline = cm.activeNetwork != null }
}
