package com.segurancarural.gpstracker.util

/**
 * Compares dotted version strings (e.g. 1.5.0 vs 1.0.0-alpha).
 * Returns > 0 if [remote] is newer than [local].
 */
fun compareVersions(remote: String, local: String): Int {
    val remoteParts = parseVersionParts(remote)
    val localParts = parseVersionParts(local)
    val len = maxOf(remoteParts.size, localParts.size)
    for (i in 0 until len) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r != l) return r.compareTo(l)
    }
    return 0
}

fun isRemoteVersionNewer(remote: String, local: String): Boolean =
    compareVersions(remote, local) > 0

private fun parseVersionParts(version: String): List<Int> =
    version.trim().removePrefix("v")
        .split('.', '-', '_')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
