package app.viora.update

import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

internal object UpdatePolicy {
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    fun accepts(installedPackage: String, installedVersion: Long, installedKeys: Set<String>, candidatePackage: String, candidateVersion: Long, candidateKeys: Set<String>): Boolean =
        installedPackage == candidatePackage && candidateVersion > installedVersion &&
            installedKeys.isNotEmpty() && installedKeys == candidateKeys

    fun newerRelease(candidate: String, installed: String?): Boolean {
        fun parts(value: String): List<Int>? {
            if (!value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) return null
            return value.split('.').map { it.toIntOrNull() ?: return null }
        }
        val next = parts(candidate) ?: return false
        val current = parts(installed.orEmpty()) ?: return true
        return next.zip(current).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a > b } ?: false
    }

    fun releaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.port == -1 && uri.query == null && uri.fragment == null &&
            uri.rawPath.startsWith("/dumbly-smart/viora/releases/download/") &&
            !uri.rawPath.contains("..") && !uri.rawPath.contains('%')
    }.getOrDefault(false)

    fun matches(input: InputStream, size: Long, expected: String): Boolean = input.use {
        if (size !in 1..MAX_APK_BYTES || !expected.matches(Regex("[a-fA-F0-9]{64}"))) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val read = it.read(buffer)
            if (read == -1) break
            count += read
            if (count > size) return false
            digest.update(buffer, 0, read)
        }
        count == size && digest.digest().joinToString("") { byte -> "%02x".format(byte) }.equals(expected, true)
    }
}
