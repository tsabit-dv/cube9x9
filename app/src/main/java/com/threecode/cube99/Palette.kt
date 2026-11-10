package com.threecode.cube99

object Palette {
    val colors = intArrayOf(
        0xFFFFFFFF.toInt(), // putih
        0xFFFFD500.toInt(), // kuning
        0xFFD32F2F.toInt(), // merah
        0xFFFF8F00.toInt(), // oranye
        0xFF1565C0.toInt(), // biru
        0xFF2E7D32.toInt(), // hijau
    )
    val names = arrayOf("Putih", "Kuning", "Merah", "Oranye", "Biru", "Hijau")

    /** Skema standar per sisi U R F D L B: putih atas, merah kanan, hijau depan, kuning bawah, oranye kiri, biru belakang. */
    val defaultFaceColor = intArrayOf(0, 2, 5, 1, 3, 4)

    fun isLight(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 > 150
    }
}
