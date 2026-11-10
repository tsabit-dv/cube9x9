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

    /** Kunci identitas wing: (orbit, warna facelet alpha, warna facelet beta). Tiap wing fisik punya kunci unik. */
    private fun identityKey(orbit: Int, a: Int, b: Int) = (orbit.toLong() * 6 + a) * 6 + b

    /**
     * Paritas (0/1) tiap orbit untuk [state] terhadap target [wt] (warna target per facelet wing).
     *
     * Dalam satu orbit ada 24 wing yang semuanya dapat dibedakan (pasangan warna berurut menurut
     * label alpha/beta yang ikut berputar bersama wing, jadi sudah memuat kiralitas). Karena itu
     * duplikasi pasangan warna tidak pernah muncul pada kubus legal, dan selalu berarti input salah.
     * Kondisi itu ditolak, bukan ditebak.
     */
    fun parities(state: IntArray, wt: IntArray): IntArray {
        val home = HashMap<Long, Int>()
        for (key in cubies.keys) {
            val k = identityKey(orbitOf.getValue(key), wt[alpha.getValue(key)], wt[beta(key)])
            val prev = home.put(k, key)
            if (prev != null) {
                throw SolveException(
                    "Edge tengah tidak unik: ${describeCubie(prev)} dan ${describeCubie(key)} punya pasangan warna sama pada target. " +
                        "Cek stiker edge di dua posisi itu."
                )
            }
        }
        val dest = HashMap<Int, Int>()
        val taken = HashMap<Int, Int>()
        for (key in cubies.keys) {
            val k = identityKey(orbitOf.getValue(key), state[alpha.getValue(key)], state[beta(key)])
            val h = home[k] ?: throw SolveException("Warna wing di ${describeCubie(key)} tidak cocok dengan edge manapun (cek input stiker)")
            val prev = taken.put(h, key)
            if (prev != null) {
                throw SolveException("Wing di ${describeCubie(prev)} dan ${describeCubie(key)} berwarna sama (mustahil pada kubus asli). Cek stiker di dua posisi itu.")
            }
            dest[key] = h
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
            result[orbitOf.getValue(key)] = result[orbitOf.getValue(key)] xor ((len - 1) and 1)
        }
        return result
    }

    /**
     * Periksa [state] (warna sudah dinormalisasi jadi indeks sisi) terhadap kubus selesai: tiap wing
     * fisik harus muncul tepat satu kali. Mengembalikan daftar masalah dengan lokasi stiker.
     */
    fun checkIdentity(state: IntArray): List<String> {
        val problems = ArrayList<String>()
        val home = HashMap<Long, Int>()
        for (key in cubies.keys) {
            val a = alpha.getValue(key)
            home[identityKey(orbitOf.getValue(key), model.faceOf(a), model.faceOf(beta(key)))] = key
        }
        val seenAt = HashMap<Long, Int>()
        for (key in cubies.keys) {
            val k = identityKey(orbitOf.getValue(key), state[alpha.getValue(key)], state[beta(key)])
            if (!home.containsKey(k)) {
                problems.add("Wing di ${describeCubie(key)} berwarna mustahil (${colorPair(state, key)}). Cek dua stiker itu.")
                continue
            }
            val prev = seenAt.put(k, key)
            if (prev != null) {
                problems.add("Wing di ${describeCubie(prev)} dan ${describeCubie(key)} berwarna sama (${colorPair(state, key)}). " +
                    "Kubus asli hanya punya satu wing seperti itu; salah satu stiker keliru.")
            }
        }
        if (problems.isEmpty() && seenAt.size != home.size) problems.add("Himpunan wing tidak lengkap (cek input stiker).")
        return problems
    }

    private fun colorPair(state: IntArray, key: Int): String {
        val fs = cubies.getValue(key)
        return fs.joinToString("/") { "${CubeModel.FACE_LETTERS[state[it]]}" }
    }

    private fun describeCubie(key: Int): String =
        cubies.getValue(key).joinToString(" + ") { model.describeFacelet(it) }
}
