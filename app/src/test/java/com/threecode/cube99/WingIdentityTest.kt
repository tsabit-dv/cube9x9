package com.threecode.cube99

import com.threecode.cube99.solver.CubeModel
import com.threecode.cube99.solver.CubeModel.Companion.B
import com.threecode.cube99.solver.CubeModel.Companion.D
import com.threecode.cube99.solver.CubeModel.Companion.F
import com.threecode.cube99.solver.CubeModel.Companion.L
import com.threecode.cube99.solver.CubeModel.Companion.R
import com.threecode.cube99.solver.CubeModel.Companion.U
import com.threecode.cube99.solver.Move
import com.threecode.cube99.solver.Reducer
import com.threecode.cube99.solver.Regions
import com.threecode.cube99.solver.SolveException
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** Audit identitas wing dan koordinat 9x9: kubus legal tidak boleh ditolak, kubus ilegal harus tetap ditolak. */
class WingIdentityTest {
    private val r = Reducer(9)
    private val m = r.model
    private val reg = Regions(m)

    private fun scramble(rnd: Random, count: Int, layers: IntArray? = null): IntArray {
        val s = m.solvedState()
        repeat(count) {
            val layer = if (layers == null) rnd.nextInt(m.n) else layers[rnd.nextInt(layers.size)]
            m.apply(s, Move(rnd.nextInt(3), layer, 1 + rnd.nextInt(3)))
        }
        return s
    }

    private fun assertSolves(s: IntArray, label: String) {
        val chk = r.normalize(s)
        m.applyAll(chk, r.solve(s))
        assertTrue("$label tidak selesai", m.isSolved(chk))
    }

    /** Dua stiker wing satu keping: daftar [facelet, facelet]. */
    private fun wingCubies(): List<List<Int>> =
        (0 until m.size).filter { reg.wing[it] }.groupBy { m.cubieKey(it) }.values.map { it.sorted() }

    // ---- koordinat: tiap pasangan sisi bersebelahan (tabel jaring standar, ditulis tangan) ----

    @Test fun adjacentFacesShareEdgeCubies() {
        val e = 8
        // (sisi, baris, kolom) di dua sisi harus satu keping fisik yang sama
        fun same(f1: Int, r1: Int, c1: Int, f2: Int, r2: Int, c2: Int) = assertEquals(
            "${CubeModel.FACE_LETTERS[f1]}($r1,$c1) vs ${CubeModel.FACE_LETTERS[f2]}($r2,$c2)",
            m.cubieKey(m.idx(f1, r1, c1)), m.cubieKey(m.idx(f2, r2, c2))
        )
        for (i in 0..e) {
            same(U, e, i, F, 0, i)
            same(U, i, e, R, 0, e - i)
            same(U, 0, i, B, 0, e - i)
            same(U, i, 0, L, 0, i)
            same(F, i, e, R, i, 0)
            same(F, i, 0, L, i, e)
            same(B, i, 0, R, i, e)
            same(B, i, e, L, i, 0)
            same(D, 0, i, F, e, i)
            same(D, i, e, R, e, i)
            same(D, e, i, B, e, e - i)
            same(D, i, 0, L, e, e - i)
        }
    }

    @Test fun reportedWingPairsAreRealWings() {
        // contoh laporan (1-based): U(1,6)+B(1,4), U(4,1)+L(1,4), U(1,7)+B(1,3), L(3,1)+B(3,9)
        assertEquals(m.cubieKey(m.idx(U, 0, 5)), m.cubieKey(m.idx(B, 0, 3)))
        assertEquals(m.cubieKey(m.idx(U, 3, 0)), m.cubieKey(m.idx(L, 0, 3)))
        assertEquals(m.cubieKey(m.idx(U, 0, 6)), m.cubieKey(m.idx(B, 0, 2)))
        assertEquals(m.cubieKey(m.idx(L, 2, 0)), m.cubieKey(m.idx(B, 2, 8)))
        assertTrue(reg.wing[m.idx(U, 0, 5)] && reg.wing[m.idx(L, 0, 3)])
    }

    @Test fun wingCountsMatch9x9Geometry() {
        val w = wingCubies()
        assertEquals(72, w.size)                       // 12 edge x 6 wing
        assertTrue(w.all { it.size == 2 })
        val byPair = w.groupBy { c -> c.map { m.faceOf(it) }.toSet() }
        assertEquals(12, byPair.size)
        assertTrue(byPair.values.all { it.size == 6 })  // 6 wing tiap pasangan warna
    }

    // ---- A. valid ----

    @Test fun solvedAndRandomCubesAreValid() {
        assertEquals(emptyList<String>(), r.validate(m.solvedState()))
        val rnd = Random(2024)
        repeat(300) { i -> assertEquals("i=$i", emptyList<String>(), r.validate(scramble(rnd, 1 + rnd.nextInt(600)))) }
    }

    // ---- B. kombinasi warna wing sama (kiralitas beda) tetap valid ----

    @Test fun sameColorPairWingsInDifferentPlacesAreValid() {
        val rnd = Random(31)
        var sawSwap = false
        repeat(60) {
            val s = scramble(rnd, 50 + rnd.nextInt(400))
            // beberapa wing berpasangan warna (tak berurut) sama pasti ada: 6 wing per pasangan warna
            val pairs = wingCubies().groupBy { c -> c.map { s[it] }.toSet() }
            assertTrue(pairs.values.all { it.size == 6 })
            // ada wing yang pasangan warnanya sama dengan wing lain pada orbit yang sama; tetap sah
            if (pairs.values.any { it.size > 1 }) sawSwap = true
            assertEquals(emptyList<String>(), r.validate(s))
        }
        assertTrue(sawSwap)
    }

    // ---- C. permutasi wing berbeda: hanya slab dalam ----

    @Test fun wingPermutationsFromInnerSlabsAreValidAndSolvable() {
        val rnd = Random(77)
        repeat(25) { i ->
            val s = scramble(rnd, 5 + rnd.nextInt(150), intArrayOf(1, 2, 3, 5, 6, 7))
            assertEquals(emptyList<String>(), r.validate(s))
            assertSolves(s, "slab i=$i")
        }
    }

    // ---- D. orientasi diputar: rotasi seluruh kubus ----

    @Test fun wholeCubeRotationsStayValid() {
        val rnd = Random(5)
        val s = scramble(rnd, 120)
        for (axis in 0..2) for (t in 1..3) {
            val rot = s.copyOf()
            for (layer in 0 until m.n) m.apply(rot, Move(axis, layer, t))
            assertEquals("axis=$axis t=$t", emptyList<String>(), r.validate(rot))
            assertSolves(rot, "rot axis=$axis t=$t")
        }
    }

    @Test fun recoloredCenterSchemeStaysValid() {
        // warna fisik sembarang: validasi hanya bergantung pada warna center
        val s = scramble(Random(9), 200)
        val perm = intArrayOf(40, 41, 42, 43, 44, 45).also { a -> a.reverse() }
        assertEquals(emptyList<String>(), r.validate(IntArray(s.size) { perm[s[it]] }))
    }

    @Test fun rotatedFaceGridIsRejected() {
        // satu sisi dicatat terputar 90 derajat (salah orientasi saat input) harus ditolak
        val s = scramble(Random(12), 300)
        for (f in 0 until 6) {
            val t = s.copyOf()
            for (row in 0 until 9) for (col in 0 until 9) t[m.idx(f, row, col)] = s[m.idx(f, 8 - col, row)]
            if (t.contentEquals(s)) continue
            val problems = r.validate(t)
            assertTrue("sisi ${CubeModel.FACE_LETTERS[f]} terputar harus ditolak", problems.isNotEmpty())
        }
    }

    // ---- E. satu atau lebih stiker salah ----

    @Test fun wrongStickerCountIsRejected() {
        val s = scramble(Random(3), 200)
        val w = wingCubies()[0][0]
        val t = s.copyOf(); t[w] = (s[w] + 1) % 6
        try { r.validate(t); fail("jumlah stiker salah harus ditolak") } catch (_: SolveException) {}
    }

    @Test fun swappedStickersOfDifferentWingsAreRejectedOrLegal() {
        val rnd = Random(21)
        val pool = (0 until m.size).filter { reg.wing[it] }
        var rejected = 0
        repeat(80) {
            val s = scramble(rnd, 250)
            var a: Int; var b: Int
            do { a = pool[rnd.nextInt(pool.size)]; b = pool[rnd.nextInt(pool.size)] } while (s[a] == s[b])
            val t = s.copyOf(); t[a] = s[b]; t[b] = s[a]
            if (r.validate(t).isNotEmpty()) {
                rejected++
                try { r.solve(t); fail("solve harus menolak") } catch (_: SolveException) {}
            }
        }
        assertTrue("terlalu sedikit yang ditolak: $rejected", rejected > 70)
    }

    // ---- F. edge ilegal ----

    @Test fun flippedWingInPlaceIsRejected() {
        val rnd = Random(8)
        repeat(30) {
            val s = scramble(rnd, 200)
            val w = wingCubies()[rnd.nextInt(72)]
            val t = s.copyOf(); t[w[0]] = s[w[1]]; t[w[1]] = s[w[0]]
            val problems = r.validate(t)
            assertTrue("wing terbalik harus ditolak", problems.isNotEmpty())
            assertTrue(problems.first(), problems.first().contains("Wing"))
        }
    }

    @Test fun duplicateWingMessageNamesMissingWing() {
        // balik wing U(1,8)+B(1,2) di tempat: sama persis dengan wing U(1,2)+B(1,8) pada orbit yang sama
        val t = m.solvedState()
        val u = m.idx(U, 0, 7); val bk = m.idx(B, 0, 1)
        t[u] = B; t[bk] = U
        val problems = r.validate(t)
        assertTrue(problems.toString(), problems.any { it.contains("wing yang sama persis") })
        assertTrue(problems.toString(), problems.any { it.contains("tidak muncul") })
    }

    @Test fun parityStillRejectedWhenCornersAndEdgesDisagree() {
        val s = m.solvedState()
        // tukar dua sudut (U,R,F)-(U,L,F) : paritas sudut ganjil, edge tengah genap
        val c1 = intArrayOf(m.idx(U, 8, 8), m.idx(R, 0, 0), m.idx(F, 0, 8))
        val c2 = intArrayOf(m.idx(U, 8, 0), m.idx(F, 0, 0), m.idx(L, 0, 8))
        val t = s.copyOf()
        for (k in 0 until 3) { t[c1[k]] = s[c2[k]]; t[c2[k]] = s[c1[k]] }
        assertTrue(r.validate(t).isNotEmpty())
    }
}
