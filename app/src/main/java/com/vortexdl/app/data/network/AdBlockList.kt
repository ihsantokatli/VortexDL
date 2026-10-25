package com.vortexdl.app.data.network

import java.net.URI
import java.util.Locale

/**
 * Host-based ad and popup blocker used by [com.vortexdl.app.presentation.browser.VortexDLWebViewClient].
 *
 * Matching is suffix based, so `ads.example.doubleclick.net` is blocked together with
 * `doubleclick.net` while look-alike domains such as `notdoubleclick.net` are not.
 */
object AdBlockList {

    private val BLOCKED_HOSTS = setOf(
        "2mdn.net",
        "adcolony.com",
        "adform.net",
        "adnxs.com",
        "adsrvr.org",
        "adsterra.com",
        "amazon-adsystem.com",
        "applovin.com",
        "appnexus.com",
        "criteo.com",
        "criteo.net",
        "doubleclick.net",
        "exoclick.com",
        "googleadservices.com",
        "googlesyndication.com",
        "hilltopads.net",
        "inmobi.com",
        "juicyads.com",
        "mgid.com",
        "moatads.com",
        "onclckds.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerpops.com",
        "quantserve.com",
        "revcontent.com",
        "scorecardresearch.com",
        "startapp.com",
        "taboola.com",
        "tapjoy.com",
        "trafficjunky.net",
        "unityads.unity3d.com",
        "vungle.com",
        "zergnet.com",
    )

    fun isBlocked(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return BLOCKED_HOSTS.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    private fun hostOf(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull() ?: return null
        return host.lowercase(Locale.US).trimEnd('.')
    }
}
