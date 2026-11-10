package com.threecode.cube99

import android.content.Context

/** Penyimpanan sederhana: warna input terakhir, serta sesi penyelesaian (warna + langkah terakhir). */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("cube99", Context.MODE_PRIVATE)

    fun saveInput(colors: IntArray) = prefs.edit().putString("input", encode(colors)).apply()
    fun loadInput(size: Int): IntArray? = decode(prefs.getString("input", null), size)

    fun startSession(colors: IntArray) =
        prefs.edit().putString("session", encode(colors)).putInt("step", 0).apply()

    fun loadSession(size: Int): IntArray? = decode(prefs.getString("session", null), size)

    var step: Int
        get() = prefs.getInt("step", 0)
        set(v) = prefs.edit().putInt("step", v).apply()

    private fun encode(a: IntArray) = String(CharArray(a.size) { '0' + a[it] })

    private fun decode(s: String?, size: Int): IntArray? {
        if (s == null || s.length != size) return null
        val out = IntArray(size)
        for (i in 0 until size) {
            val d = s[i] - '0'
            if (d !in 0..5) return null
            out[i] = d
        }
        return out
    }
}
