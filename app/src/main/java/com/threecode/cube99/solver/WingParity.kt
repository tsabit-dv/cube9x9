package com.threecode.cube99.solver

/**
 * Paritas permutasi wing per orbit relatif terhadap susunan target (cocok dengan edge tengah).
 * Tiap wing adalah potongan berbeda (kiralitas), jadi paritas ganjil tidak bisa dibereskan 3-siklus wing.
 */
class WingParity(private val model: CubeModel, private val reg: Regions) {
    private val cubies = (0 until model.size).filter { reg.wing[it] }.groupBy { model.cubieKey(it) }
    private val alpha = HashMap<Int, Int>()   // kunci kubus -> facelet alpha
    private val orbitOf = HashMap<Int, Int>() // kunci kubus -> id orbit
    val orbitCount: Int

    /** Per orbit: satu gerakan slab-dalam (seperempat putaran) yang hanya menggeser wing orbit itu. */
    val fixMoves: Array<Move?>

    init {
        val gens = MacroLibrary.outerMoves(model).filter { it.turns == 1 } + MacroLibrary.innerMoves(model).filter { it.turns == 1 }
        var orbits = 0
        for ((key, fs) in cubies) {
            if (alpha.containsKey(key)) continue
            alpha[key] = fs.min()
            orbitOf[key] = orbits
            val queue = ArrayDeque<Int>()
            queue.add(key)
            while (queue.isNotEmpty()) {
                val k = queue.removeFirst()
                val a = alpha.getValue(k)
                for (g in gens) {
                    val p = model.perm(g)
                    val a2 = p[a]
                    val k2 = model.cubieKey(a2)
                    if (!alpha.containsKey(k2)) {
                        alpha[k2] = a2
                        orbitOf[k2] = orbits
                        queue.add(k2)
                    } else check(alpha.getValue(k2) == a2) { "label wing tidak konsisten" }
                }
            }
            orbits++
        }
        orbitCount = orbits

        val fix = arrayOfNulls<Move>(orbits)
        for (g in MacroLibrary.innerMoves(model).filter { it.turns == 1 }) {
            val p = model.perm(g)
            val hit = HashSet<Int>()
            for (i in 0 until model.size) if (reg.wing[i] && p[i] != i) hit.add(orbitOf.getValue(model.cubieKey(i)))
            if (hit.size == 1 && fix[hit.first()] == null) fix[hit.first()] = g
        }
        fixMoves = fix
    }

    private fun beta(key: Int): Int {
        val fs = cubies.getValue(key)
        val a = alpha.getValue(key)
        return if (fs[0] == a) fs[1] else fs[0]
    }

    /** Paritas (0/1) tiap orbit untuk [state] terhadap target [wt] (warna target per facelet wing). */
    fun parities(state: IntArray, wt: IntArray): IntArray {
        // Satu pasangan warna dapat mewakili lebih dari satu wing fisik dalam orbit
        // yang sama. Itu normal pada kubus besar: wing-wing tersebut tidak dapat
        // dibedakan hanya dari warna. Simpan semua kandidat, jangan menolak duplikasi.
        val home = HashMap<Long, ArrayDeque<Int>>()
        for (key in cubies.keys.sorted()) {
            val orbit = orbitOf.getValue(key)
            val k = (orbit.toLong() * 6 + wt[alpha.getValue(key)]) * 6 + wt[beta(key)]
            home.getOrPut(k) { ArrayDeque() }.addLast(key)
        }

        val dest = HashMap<Int, Int>()
        val ambiguousOrbit = BooleanArray(orbitCount)
        for ((k, candidates) in home) {
            if (candidates.size > 1) {
                // Identitas wing dalam bucket ini tidak dapat diketahui dari stiker.
                // Paritas orbit menjadi tak-tertentu; hindari koreksi parity palsu.
                val orbit = (k / 36L).toInt()
                ambiguousOrbit[orbit] = true
            }
        }
        for (key in cubies.keys.sorted()) {
            val orbit = orbitOf.getValue(key)
            val k = (orbit.toLong() * 6 + state[alpha.getValue(key)]) * 6 + state[beta(key)]
            val candidates = home[k]
                ?: throw SolveException("Warna wing tidak cocok dengan edge manapun (cek input stiker)")
            if (candidates.isEmpty()) throw SolveException("Jumlah wing dengan pasangan warna tidak seimbang (cek input stiker)")
            dest[key] = candidates.removeFirst()
        }
        if (home.values.any { it.isNotEmpty() }) {
            throw SolveException("Jumlah wing dengan pasangan warna tidak seimbang (cek input stiker)")
        }
        val result = IntArray(orbitCount)
        val seen = HashSet<Int>()
        for (key in cubies.keys) {
            if (!seen.add(key)) continue
            var len = 1
            var j = dest.getValue(key)
            while (j != key) {
                if (!seen.add(j)) throw SolveException("Wing tidak membentuk permutasi (cek input stiker)")
                j = dest.getValue(j)
                len++
            }
            val orbit = orbitOf.getValue(key)
            result[orbit] = result[orbit] xor ((len - 1) and 1)
        }
        // Jika ada wing identik dalam orbit, parity tidak dapat ditentukan dari
        // warna saja. Memilih parity genap adalah matching valid dan mencegah
        // algoritma melakukan gerakan koreksi yang justru merusak pairing.
        for (o in 0 until orbitCount) if (ambiguousOrbit[o]) result[o] = 0
        return result
    }
}
