package com.painhunt.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private fun probeOnline(): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 1500) }
    true
}.getOrDefault(false)

@Composable
fun rememberIsOnline(): Pair<Boolean, () -> Unit> {
    var isOnline by remember { mutableStateOf(true) }
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(ticker) {
        isOnline = withContext(Dispatchers.IO) { probeOnline() }
        while (true) {
            delay(15_000)
            isOnline = withContext(Dispatchers.IO) { probeOnline() }
        }
    }
    return Pair(isOnline) { ticker++ }
}
