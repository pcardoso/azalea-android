package pt.grok.sway.data

import android.net.Uri

/**
 * Prescription links from the email: https://app.gowithsway.com/apps/open/CODE (an App Link,
 * so the app opens straight from the email) and sway://prescription/CODE (tried by the web page
 * when the App Link was skipped, e.g. by an in-app browser).
 */
object DeepLinks {
    fun prescriptionCode(uri: Uri?): String? {
        if (uri == null) return null
        val segments = uri.pathSegments
        val code = when {
            uri.scheme == "sway" && uri.host == "prescription" -> segments.firstOrNull()
            uri.scheme == "https" && segments.size >= 3 && segments[0] == "apps" && segments[1] == "open" -> segments[2]
            else -> null
        }
        return code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    }
}
