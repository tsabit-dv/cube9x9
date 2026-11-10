package com.threecode.cube99.solver

/** Klasifikasi facelet: center, sudut, edge tengah, wing. */
class Regions(val model: CubeModel) {
    private val n = model.n
    val size = model.size
    val center = BooleanArray(size)     // center yang bisa bergerak (di luar pusat sisi)
    val corner = BooleanArray(size)
    val middleEdge = BooleanArray(size)
    val wing = BooleanArray(size)
    val fixedCenter = BooleanArray(size)

    init {
        val mid = model.mid
        for (i in 0 until size) {
            val r = model.rowOf(i)
            val c = model.colOf(i)
            val rEdge = r == 0 || r == n - 1
            val cEdge = c == 0 || c == n - 1
            when {
                rEdge && cEdge -> corner[i] = true
                rEdge -> if (c == mid) middleEdge[i] = true else wing[i] = true
                cEdge -> if (r == mid) middleEdge[i] = true else wing[i] = true
                r == mid && c == mid -> fixedCenter[i] = true
                else -> center[i] = true
            }
        }
    }
}
