package com.segurancarural.gpstracker.util

/** First letter shown inside the map marker circle. */
fun markerInitial(label: String): String {
    val trimmed = label.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.first().uppercaseChar().toString()
}
