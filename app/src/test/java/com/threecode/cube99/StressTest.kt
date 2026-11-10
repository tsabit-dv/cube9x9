package com.threecode.cube99

import com.threecode.cube99.solver.CubeModel
import com.threecode.cube99.solver.Move
import com.threecode.cube99.solver.Reducer
import com.threecode.cube99.solver.SolveException
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class StressTest {
    private fun scramble(m: CubeModel, rnd: Random, count: Int, layers: IntArray? = null): IntArray {
        val s = m.solvedState()
        repeat(count) {
            val layer = if (layers == null) rnd.nextInt(m.n) else layers[rnd.nextInt(layers.size)]
            m.apply(s, Move(rnd.nextInt(3), layer, 1 + rnd.nextInt(3)))
        }
        return s
    }

    private fun solveAndCheck(r: Reducer, s: IntArray, label: String) {
        val sol = r.solve(s)
        val chk = r.normalize(s)
        r.model.applyAll(chk, sol)
        assertTrue("$label tidak selesai", r.model.isSolved(chk))
    }

    @Test fun legalCubesAreAlwaysValid() {
        for (n in intArrayOf(5, 7, 9)) {
            val r = Reducer(n)
            val rnd = Random(7L * n)
            repeat(100) { i ->
                val s = scramble(r.model, rnd, 1 + rnd.nextInt(300))
                assertEquals("n=$n i=$i", emptyList<String>(), r.validate(s))
            }
        }
    }

    @Test fun sweepAll9() {
        val r = Reducer(9)
        val rnd = Random(99)
        for (i in 0 until 150) {
            val count = intArrayOf(1, 2, 3, 5, 20, 100, 500, 1500)[i % 8]
            solveAndCheck(r, scramble(r.model, rnd, count), "9 i=$i count=$count")
        }
    }

    @Test fun innerSlabsOnly9() {
        val r = Reducer(9)
        val rnd = Random(5)
        for (i in 0 until 60) solveAndCheck(r, scramble(r.model, rnd, 5 + rnd.nextInt(200), intArrayOf(1, 2, 3, 5, 6, 7)), "slab i=$i")
    }

    @Test fun middleSlabOnly9() {
        val r = Reducer(9)
        val rnd = Random(6)
        for (i in 0 until 30) solveAndCheck(r, scramble(r.model, rnd, 1 + rnd.nextInt(20), intArrayOf(4)), "mid i=$i")
    }

    @Test fun oddParityOrbit9() {
        // satu seperempat putaran slab dalam menghasilkan paritas wing ganjil
        val r = Reducer(9)
        for (layer in intArrayOf(1, 2, 3, 5, 6, 7)) {
            val s = r.model.solvedState()
            r.model.apply(s, Move(0, layer, 1))
            solveAndCheck(r, s, "slab layer=$layer")
        }
    }

    /** Stiker palsu tidak boleh menghasilkan "solusi" salah: harus ditolak dengan pesan. */
    @Test fun illegalInputsAreRejected() {
        val r = Reducer(9)
        val rnd = Random(11)
        var rejected = 0
        repeat(80) {
            val s = scramble(r.model, rnd, 200)
            // tukar dua stiker berwarna beda di wing/edge/sudut (bukan center): jaga jumlah warna tetap
            val reg = com.threecode.cube99.solver.Regions(r.model)
            val pool = (0 until r.model.size).filter { reg.wing[it] || reg.middleEdge[it] || reg.corner[it] }
            var a: Int; var b: Int
            do { a = pool[rnd.nextInt(pool.size)]; b = pool[rnd.nextInt(pool.size)] } while (s[a] == s[b])
            val t = s.copyOf(); t[a] = s[b]; t[b] = s[a]
            val problems = r.validate(t)
            if (problems.isNotEmpty()) {
                rejected++
                try { r.solve(t); fail("solve harus menolak input ilegal") } catch (_: SolveException) {}
            } else {
                // tertukar jadi kubus legal lain (mis. dua wing identik secara fisik): harus tetap terpecahkan
                solveAndCheck(r, t, "swap-legal")
            }
        }
        assertTrue("terlalu sedikit input ilegal terdeteksi: $rejected", rejected > 60)
    }

    @Test fun recoloredSolvedCubeIsStillSolved() {
        val r = Reducer(9)
        val m = r.model
        val colors = IntArray(m.size) { m.solvedState()[it] }
        for (i in colors.indices) colors[i] = when (colors[i]) { CubeModel.R -> CubeModel.L; CubeModel.L -> CubeModel.R; else -> colors[i] }
        assertEquals(emptyList<String>(), r.validate(colors))
        assertTrue(r.solve(colors).isEmpty())
    }

    @Test fun singleCornerTwistRejected() {
        val r = Reducer(9)
        val m = r.model
        val s = m.solvedState()
        // putar satu sudut (U,R,F kanan-bawah-U) di tempat: tukar warna siklik tiga stiker
        val a = m.idx(CubeModel.U, 8, 8); val b = m.idx(CubeModel.R, 0, 0); val c = m.idx(CubeModel.F, 0, 8)
        val t = s.copyOf(); t[a] = s[b]; t[b] = s[c]; t[c] = s[a]
        val problems = r.validate(t)
        assertTrue(problems.isNotEmpty())
        println(problems.first())
    }

    @Test fun singleEdgeFlipRejected() {
        val r = Reducer(9)
        val m = r.model
        val s = m.solvedState()
        val a = m.idx(CubeModel.U, 8, 4); val b = m.idx(CubeModel.F, 0, 4)
        val t = s.copyOf(); t[a] = s[b]; t[b] = s[a]
        val problems = r.validate(t)
        assertTrue(problems.isNotEmpty())
        println(problems.first())
    }

    @Test fun twoWingsSwappedRejectedWithLocation() {
        val r = Reducer(9)
        val m = r.model
        val s = m.solvedState()
        val a = m.idx(CubeModel.U, 8, 1); val b = m.idx(CubeModel.U, 8, 2)
        // dua wing berorbit beda pada edge yang sama: tukar stiker U-nya tidak mengubah apa pun (warna sama)
        val t = s.copyOf(); t[a] = s[m.idx(CubeModel.F, 0, 1)]; t[m.idx(CubeModel.F, 0, 1)] = s[a]
        val problems = r.validate(t)
        assertTrue(problems.isNotEmpty())
        println(problems.first())
    }
}
