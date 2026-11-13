package io.github.soundtouchradio.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class FoundVia { SSDP, MDNS, MANUAL, REMEMBERED }

data class Candidate(val host: String, val via: FoundVia, val location: String? = null, val label: String? = null)

/** Descubre candidatos por SSDP y mDNS en paralelo. La validación real la hace [Linker]. */
class Discovery(private val context: Context) {

    fun discover(timeoutMs: Long = 5_000): Flow<Candidate> = channelFlow {
        launch(Dispatchers.IO) { ssdp(timeoutMs) { send(it) } }
        launch { mdns(timeoutMs).collect { send(it) } }
    }

    private suspend fun ssdp(timeoutMs: Long, emit: suspend (Candidate) -> Unit) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("soundtouch-ssdp").apply { setReferenceCounted(false) }
        val found = mutableListOf<Candidate>()
        try {
            lock.acquire()
            Ssdp.search(timeoutMs) { r -> synchronized(found) { found += Candidate(r.host, FoundVia.SSDP, r.location, r.server) } }
        } catch (_: Exception) {
            // Sin Wi-Fi o multicast bloqueado: mDNS y la IP manual siguen disponibles.
        } finally {
            runCatching { lock.release() }
        }
        synchronized(found) { found.distinctBy { it.host } }.forEach { emit(it) }
    }

    /** M-SEARCH unicast a una IP concreta, para obtener la URL de descripción UPnP. */
    fun ssdpUnicast(host: String, timeoutMs: Long = 1_500): String? {
        var location: String? = null
        runCatching { Ssdp.search(timeoutMs, host) { if (it.host == host && location == null) location = it.location } }
        return location
    }

    private fun mdns(timeoutMs: Long): Flow<Candidate> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val queue = ConcurrentLinkedQueue<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)

        fun resolveNext() {
            if (!resolving.compareAndSet(false, true)) return
            val next = queue.poll() ?: run { resolving.set(false); return }
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) { resolving.set(false); resolveNext() }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull()?.hostAddress
                    else @Suppress("DEPRECATION") info.host?.hostAddress
                    if (host != null) trySend(Candidate(host, FoundVia.MDNS, label = info.serviceName))
                    resolving.set(false); resolveNext()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) { queue.add(info); resolveNext() }
        }
        runCatching { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { close() }
        val timer = launch { kotlinx.coroutines.delay(timeoutMs); close() }
        awaitClose {
            timer.cancel()
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }.flowOn(Dispatchers.Main)

    companion object { const val SERVICE = "_soundtouch._tcp" }
}
