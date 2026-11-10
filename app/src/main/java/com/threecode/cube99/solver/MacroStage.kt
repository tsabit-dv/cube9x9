package com.threecode.cube99.solver

import java.util.Random

/**
 * Makro = setup (gerakan sisi luar) + inti + kebalikan setup. Efeknya disimpan sebagai pasangan
 * (src -> dst) hanya untuk facelet yang dilacak, supaya evaluasi cepat.
 */
/** Sekumpulan setup dan inti yang dikalikan menjadi makro. */
class MacroGroup(val setups: List<List<Move>>, val cores: List<List<Move>>)

class MacroLibrary(
    private val model: CubeModel,
    private val groups: List<MacroGroup>,
    tracked: BooleanArray,
) {
    constructor(model: CubeModel, setups: List<List<Move>>, cores: List<List<Move>>, tracked: BooleanArray) :
        this(model, listOf(MacroGroup(setups, cores)), tracked)

    val count: Int
    private val macroGroup: IntArray
    private val macroSetup: IntArray
    private val macroCore: IntArray
    val cost: IntArray
    val start: IntArray
    val src: IntArray
    val dst: IntArray
    /** id makro dengan efek kebalikan, atau -1 bila tidak ada di pustaka. */
    val inverse: IntArray

    init {
        val size = model.size
        val setupInv = groups.map { g ->
            g.setups.map { st ->
                val p = model.compose(st)
                IntArray(size).also { inv -> for (i in 0 until size) inv[p[i]] = i }
            }
        }
        val corePerm = groups.map { g -> g.cores.map { model.compose(it) } }
        val coreSupport = corePerm.map { perms ->
            perms.map { p -> (0 until size).filter { p[it] != it && tracked[it] }.toIntArray() }
        }

        // pass 1: pilih (grup, setup, core) termurah untuk tiap efek unik
        val best = HashMap<Long, Long>()
        for (g in groups.indices) {
            val grp = groups[g]
            for (s in grp.setups.indices) {
                val inv = setupInv[g][s]
                for (c in grp.cores.indices) {
                    val sup = coreSupport[g][c]
                    if (sup.isEmpty()) continue
                    val p = corePerm[g][c]
                    var h = 0L
                    for (j in sup) h += mix(inv[j].toLong() * size + inv[p[j]])
                    val cost = 2 * grp.setups[s].size + grp.cores[c].size
                    val packed = (cost.toLong() shl 52) or (g.toLong() shl 48) or (s.toLong() shl 24) or c.toLong()
                    val old = best[h]
                    if (old == null || (old ushr 52) > cost.toLong()) best[h] = packed
                }
            }
        }

        count = best.size
        macroGroup = IntArray(count)
        macroSetup = IntArray(count)
        macroCore = IntArray(count)
        cost = IntArray(count)
        start = IntArray(count + 1)
        var total = 0
        val chosen = best.values.sortedBy { it ushr 52 }
        for ((k, packed) in chosen.withIndex()) {
            val g = ((packed ushr 48) and 0xF).toInt()
            val s = ((packed ushr 24) and 0xFFFFFF).toInt()
            val c = (packed and 0xFFFFFF).toInt()
            macroGroup[k] = g; macroSetup[k] = s; macroCore[k] = c
            cost[k] = (packed ushr 52).toInt()
            total += coreSupport[g][c].size
        }
        src = IntArray(total)
        dst = IntArray(total)
        var e = 0
        for (k in 0 until count) {
            start[k] = e
            val g = macroGroup[k]
            val inv = setupInv[g][macroSetup[k]]
            val p = corePerm[g][macroCore[k]]
            for (j in coreSupport[g][macroCore[k]]) {
                src[e] = inv[j]
                dst[e] = inv[p[j]]
                e++
            }
        }
        start[count] = e

        val byHash = HashMap<Long, Int>()
        for (k in 0 until count) {
            var h = 0L
            for (x in start[k] until start[k + 1]) h += mix(src[x].toLong() * size + dst[x])
            byHash[h] = k
        }
        inverse = IntArray(count) { k ->
            var h = 0L
            for (x in start[k] until start[k + 1]) h += mix(dst[x].toLong() * size + src[x])
            byHash[h] ?: -1
        }
    }

    fun moves(id: Int): List<Move> {
        val g = groups[macroGroup[id]]
        val s = g.setups[macroSetup[id]]
        return s + g.cores[macroCore[id]] + s.reversed().map { it.inverse() }
    }

    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    companion object {
        /** Semua urutan gerakan sisi luar sepanjang 0..2, deduplikasi berdasarkan permutasi. */
        fun outerSetups(model: CubeModel, maxLen: Int = 2): List<List<Move>> {
            val outer = outerMoves(model)
            val result = ArrayList<List<Move>>()
            val seen = HashSet<Int>()
            fun add(w: List<Move>) {
                val p = model.compose(w)
                if (seen.add(p.contentHashCode())) result.add(w)
            }
            add(emptyList())
            for (a in outer) add(listOf(a))
            if (maxLen >= 2) for (a in outer) for (b in outer) {
                if (a.axis == b.axis && a.layer == b.layer) continue
                add(listOf(a, b))
            }
            return result
        }

        /** Setup lebih kaya: gerakan sisi luar dan slab dalam, sepanjang 0..2. */
        fun richSetups(model: CubeModel): List<List<Move>> {
            val moves = outerMoves(model) + innerMoves(model)
            val result = ArrayList<List<Move>>()
            val seen = HashSet<Int>()
            fun add(w: List<Move>) {
                val p = model.compose(w)
                if (seen.add(p.contentHashCode())) result.add(w)
            }
            add(emptyList())
            for (a in moves) add(listOf(a))
            for (a in moves) for (b in moves) {
                if (a.axis == b.axis && a.layer == b.layer) continue
                add(listOf(a, b))
            }
            return result
        }

        fun outerMoves(model: CubeModel): List<Move> {
            val l = ArrayList<Move>()
            for (axis in 0..2) for (layer in intArrayOf(0, model.n - 1)) for (t in 1..3) l.add(Move(axis, layer, t))
            return l
        }

        fun innerMoves(model: CubeModel, turns: IntProgression = 1..3, withMid: Boolean = false): List<Move> {
            val l = ArrayList<Move>()
            for (axis in 0..2) for (layer in 1 until model.n - 1) {
                if (layer == model.mid && !withMid) continue
                for (t in turns) l.add(Move(axis, layer, t))
            }
            return l
        }

        /**
         * Inti center: komutator [X, O S O'] yang hanya menggeser tepat 3 facelet center (3-siklus murni).
         * Wing, sudut, dan pusat sisi tidak tersentuh.
         */
        fun centerCores(model: CubeModel, reg: Regions): List<List<Move>> {
            val inner = innerMoves(model, withMid = true)
            val outer = outerMoves(model)
            val conj = ArrayList<List<Move>>()
            for (o in outer) for (s in inner) conj.add(listOf(o, s, o.inverse()))
            val xs = ArrayList<List<Move>>()
            for (s in inner) xs.add(listOf(s))
            for (o in outer) xs.add(listOf(o))
            fun inv(w: List<Move>) = w.reversed().map { it.inverse() }
            val seen = HashSet<Int>()
            val out = ArrayList<List<Move>>()
            for (x in xs) for (y in conj) {
                val w = x + y + inv(x) + inv(y)
                val p = model.compose(w)
                var moved = 0
                var bad = false
                for (i in 0 until model.size) {
                    if (p[i] == i) continue
                    if (!reg.center[i]) { bad = true; break }
                    moved++
                }
                if (bad || moved != 3) continue
                if (seen.add(p.contentHashCode())) out.add(w)
            }
            return out
        }

        /** Inti wing: komutator [S, O1 O2 O1'] yang hanya menggeser wing (3-siklus). */
        fun wingCores(model: CubeModel, reg: Regions): List<List<Move>> {
            val outer = outerMoves(model)
            val inner = innerMoves(model)
            val seen = HashSet<Int>()
            val out = ArrayList<List<Move>>()
            for (s in inner) for (o1 in outer) for (o2 in outer) {
                if (o1.axis == o2.axis && o1.layer == o2.layer) continue
                val t = listOf(o1, o2, o1.inverse())
                val ti = listOf(o1, o2.inverse(), o1.inverse())
                val w = listOf(s) + t + listOf(s.inverse()) + ti
                val p = model.compose(w)
                var moved = 0
                var ok = true
                for (i in 0 until model.size) {
                    if (p[i] == i) continue
                    moved++
                    if (!reg.wing[i]) { ok = false; break }
                }
                if (!ok || moved == 0) continue
                if (seen.add(p.contentHashCode())) out.add(w)
            }
            return out
        }
    }
}

/** Greedy: pilih makro dengan rasio perbaikan/biaya terbaik sampai semua facelet terlacak cocok target. */
class GreedyStage(
    private val model: CubeModel,
    private val lib: MacroLibrary,
    private val target: IntArray,       // warna target per facelet, -1 jika tidak dilacak
    private val state: IntArray,        // state kerja (dimodifikasi)
    private val rnd: Random = Random(12345),
) {
    private val tracked = (0 until model.size).filter { target[it] >= 0 }.toIntArray()
    private val tabu = IntArray(10) { -1 }
    private var tabuPos = 0

    private fun isTabu(m: Int): Boolean {
        for (t in tabu) if (t == m) return true
        return false
    }

    fun mismatches(): Int = tracked.count { state[it] != target[it] }

    private fun delta(m: Int): Int {
        var d = 0
        for (e in lib.start[m] until lib.start[m + 1]) {
            val t = target[lib.dst[e]]
            if (state[lib.src[e]] == t) d++
            if (state[lib.dst[e]] == t) d--
        }
        return d
    }

    private fun applyMacro(m: Int) {
        val a = lib.start[m]
        val b = lib.start[m + 1]
        val vals = IntArray(b - a) { state[lib.src[a + it]] }
        for (e in a until b) state[lib.dst[e]] = vals[e - a]
    }

    /** Kembalikan gerakan yang dipakai; null kalau macet melewati batas langkah. */
    fun run(maxSteps: Int, onProgress: (Int) -> Unit = {}): List<Move>? {
        val out = ArrayList<Move>()
        var steps = 0
        var stall = 0
        var bestWrong = Int.MAX_VALUE
        while (true) {
            val wrong = mismatches()
            onProgress(wrong)
            if (wrong == 0) return out
            if (steps++ >= maxSteps) return null
            if (wrong < bestWrong) { bestWrong = wrong; stall = 0 } else stall++

            var bestM = -1
            var bestD = 0
            var bestCost = 1
            var kickPick = -1
            var kickSeen = 0
            val kickMin = -2 - minOf(4, stall / 8)
            for (m in 0 until lib.count) {
                if (isTabu(m)) continue
                val d = delta(m)
                if (d > 0) {
                    if (bestM < 0 || d * bestCost > bestD * lib.cost[m]) {
                        bestM = m; bestD = d; bestCost = lib.cost[m]
                    }
                } else if (bestM < 0 && d >= kickMin) {
                    kickSeen++
                    if (rnd.nextInt(kickSeen) == 0) kickPick = m
                }
            }
            val pick = if (bestM >= 0) bestM else kickPick
            if (pick < 0) return null
            tabu[tabuPos] = lib.inverse[pick]
            tabuPos = (tabuPos + 1) % tabu.size
            applyMacro(pick)
            out.addAll(lib.moves(pick))
        }
    }
}
