package com.threecode.cube99.solver

/**
 * Satu gerakan lapisan: putar lapisan [layer] pada sumbu [axis] sebanyak [turns] x +90 derajat
 * (aturan tangan kanan terhadap sumbu positif).
 *
 * Sumbu: 0 = x (kanan), 1 = y (atas), 2 = z (depan). Layer 0..n-1 searah sumbu positif.
 */
data class Move(val axis: Int, val layer: Int, val turns: Int) {
    fun inverse() = Move(axis, layer, (4 - turns) % 4)
}

/**
 * Model facelet kubus n x n x n. Index facelet = face * n * n + row * n + col.
 * Urutan face sama dengan Kociemba: U R F D L B. Tata letak jaring standar
 * (U atas, L F R B tengah, D bawah).
 */
class CubeModel(val n: Int) {
    val nn = n * n
    val size = 6 * nn
    val mid = (n - 1) / 2

    private val fx = IntArray(size)
    private val fy = IntArray(size)
    private val fz = IntArray(size)
    private val fFace = IntArray(size)
    private val lookup = IntArray(n * n * n * 6) { -1 }

    // dst[i] = index tujuan facelet i setelah gerakan; cache per (axis, layer, turns)
    private val moveCache = arrayOfNulls<IntArray>(3 * n * 4)

    init {
        val c = n - 1
        for (f in 0 until 6) for (r in 0 until n) for (col in 0 until n) {
            val i = idx(f, r, col)
            when (f) {
                U -> { fx[i] = col; fy[i] = c; fz[i] = r }
                R -> { fx[i] = c; fy[i] = c - r; fz[i] = c - col }
                F -> { fx[i] = col; fy[i] = c - r; fz[i] = c }
                D -> { fx[i] = col; fy[i] = 0; fz[i] = c - r }
                L -> { fx[i] = 0; fy[i] = c - r; fz[i] = col }
                B -> { fx[i] = c - col; fy[i] = c - r; fz[i] = 0 }
            }
            fFace[i] = f
            lookup[key(fx[i], fy[i], fz[i], f)] = i
        }
    }

    fun idx(face: Int, row: Int, col: Int) = face * nn + row * n + col

    /** Kunci kubus fisik tempat facelet i berada (facelet satu kubus punya kunci sama). */
    fun cubieKey(i: Int) = (fx[i] * n + fy[i]) * n + fz[i]
    /** Koordinat kubus fisik (0..n-1) tempat facelet i berada; x kanan, y atas, z depan. */
    fun coordX(i: Int) = fx[i]
    fun coordY(i: Int) = fy[i]
    fun coordZ(i: Int) = fz[i]
    fun faceOf(i: Int) = i / nn
    fun rowOf(i: Int) = (i % nn) / n
    fun colOf(i: Int) = i % n

    private fun key(x: Int, y: Int, z: Int, f: Int) = ((x * n + y) * n + z) * 6 + f

    private fun coord(i: Int, axis: Int) = when (axis) { 0 -> fx[i]; 1 -> fy[i]; else -> fz[i] }

    private fun quarter(axis: Int, layer: Int): IntArray {
        val c = n - 1
        val dst = IntArray(size) { it }
        for (i in 0 until size) {
            if (coord(i, axis) != layer) continue
            val x = fx[i]; val y = fy[i]; val z = fz[i]
            val nv = NORMAL[fFace[i]]
            val nx = nv[0]; val ny = nv[1]; val nz = nv[2]
            val px: Int; val py: Int; val pz: Int
            val qx: Int; val qy: Int; val qz: Int
            when (axis) {
                0 -> { px = x; py = c - z; pz = y; qx = nx; qy = -nz; qz = ny }
                1 -> { px = z; py = y; pz = c - x; qx = nz; qy = ny; qz = -nx }
                else -> { px = c - y; py = x; pz = z; qx = -ny; qy = nx; qz = nz }
            }
            dst[i] = lookup[key(px, py, pz, faceOfNormal(qx, qy, qz))]
        }
        return dst
    }

    /** Permutasi gerakan: facelet i pindah ke dst[i]. Jangan dimodifikasi. */
    fun perm(m: Move): IntArray {
        val k = (m.axis * n + m.layer) * 4 + m.turns
        moveCache[k]?.let { return it }
        val q = quarter(m.axis, m.layer)
        var cur = IntArray(size) { it }
        repeat(m.turns) { cur = IntArray(size) { i -> q[cur[i]] } }
        moveCache[k] = cur
        return cur
    }

    fun apply(state: IntArray, m: Move) {
        val d = perm(m)
        val old = state.copyOf()
        for (i in 0 until size) state[d[i]] = old[i]
    }

    fun applyAll(state: IntArray, moves: List<Move>) {
        for (m in moves) apply(state, m)
    }

    /** Permutasi hasil rangkaian gerakan (dst-array). */
    fun compose(moves: List<Move>): IntArray {
        var p = IntArray(size) { it }
        for (m in moves) {
            val d = perm(m)
            val prev = p
            p = IntArray(size) { i -> d[prev[i]] }
        }
        return p
    }

    fun solvedState() = IntArray(size) { it / nn }

    fun isSolved(state: IntArray): Boolean {
        for (i in 0 until size) if (state[i] != i / nn) return false
        return true
    }

    // ---- notasi ----

    /** Gerakan permukaan: [face] di kedalaman [depth] (1 = lapisan luar), [cwQuarter] 1..3 searah jarum jam. */
    fun faceMove(face: Int, depth: Int, cwQuarter: Int): Move {
        val axis = FACE_AXIS[face]
        val positive = face == U || face == R || face == F
        val layer = if (positive) n - depth else depth - 1
        val turns = if (positive) (4 - cwQuarter) % 4 else cwQuarter
        return Move(axis, layer, turns)
    }

    /** Teks notasi WCA: "R", "3R'", "7D2" (lapisan tunggal ke-d dari sisi terdekat). */
    fun notation(m: Move): String {
        val d = describe(m)
        val depth = if (d.depth == 1) "" else d.depth.toString()
        val suffix = when (d.cwQuarter) { 1 -> ""; 2 -> "2"; else -> "'" }
        return depth + FACE_LETTERS[d.face] + suffix
    }

    class Described(val face: Int, val depth: Int, val cwQuarter: Int)

    fun describe(m: Move): Described {
        val positive = m.layer >= mid
        return if (positive) {
            val face = when (m.axis) { 0 -> R; 1 -> U; else -> F }
            Described(face, n - m.layer, (4 - m.turns) % 4)
        } else {
            val face = when (m.axis) { 0 -> L; 1 -> D; else -> B }
            Described(face, m.layer + 1, m.turns)
        }
    }

    /** Penjelasan Indonesia untuk satu gerakan. */
    fun explain(m: Move): String {
        val d = describe(m)
        val face = FACE_NAMES[d.face]
        val layer = if (d.depth == 1) "lapisan paling luar sisi $face" else "lapisan ke-${d.depth} dari sisi $face (hanya satu lapisan itu)"
        val turn = when (d.cwQuarter) {
            1 -> "searah jarum jam 90°"
            2 -> "180°"
            else -> "berlawanan jarum jam 90°"
        }
        return "Putar $layer, $turn, dilihat dari sisi $face."
    }

    /** Parse token solusi 3x3 seperti "R", "U2", "F'" menjadi gerakan lapisan luar. */
    fun parseOuter(token: String): Move {
        val face = FACE_LETTERS.indexOf(token[0])
        require(face >= 0) { "token tidak dikenal: $token" }
        val q = when {
            token.length == 1 -> 1
            token[1] == '2' -> 2
            token[1] == '\'' -> 3
            else -> error("token tidak dikenal: $token")
        }
        return faceMove(face, 1, q)
    }

    companion object {
        const val U = 0
        const val R = 1
        const val F = 2
        const val D = 3
        const val L = 4
        const val B = 5
        const val FACE_LETTERS = "URFDLB"
        val FACE_NAMES = arrayOf("Atas (U)", "Kanan (R)", "Depan (F)", "Bawah (D)", "Kiri (L)", "Belakang (B)")
        val FACE_AXIS = intArrayOf(1, 0, 2, 1, 0, 2)
        private val NORMAL = arrayOf(
            intArrayOf(0, 1, 0), intArrayOf(1, 0, 0), intArrayOf(0, 0, 1),
            intArrayOf(0, -1, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 0, -1),
        )

        private fun faceOfNormal(x: Int, y: Int, z: Int): Int {
            for (f in 0 until 6) {
                val v = NORMAL[f]
                if (v[0] == x && v[1] == y && v[2] == z) return f
            }
            error("normal tidak valid")
        }

        /** Gabungkan gerakan bersebelahan pada sumbu & lapisan yang sama, buang yang jadi nol. */
        fun simplify(moves: List<Move>): List<Move> {
            val out = ArrayList<Move>(moves.size)
            for (m in moves) {
                var merged = false
                var j = out.size - 1
                while (j >= 0 && out[j].axis == m.axis) {
                    if (out[j].layer == m.layer) {
                        val t = (out[j].turns + m.turns) % 4
                        if (t == 0) out.removeAt(j) else out[j] = Move(m.axis, m.layer, t)
                        merged = true
                        break
                    }
                    j--
                }
                if (!merged) out.add(m)
            }
            return out
        }
    }
}
