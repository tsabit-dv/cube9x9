package com.threecode.cube99

import com.threecode.cube99.solver.CubeModel
import com.threecode.cube99.solver.Reducer
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ReducerTest {
    private fun scramble(m: CubeModel, rnd: Random, count: Int): IntArray {
        val s = m.solvedState()
        repeat(count) {
            val axis = rnd.nextInt(3)
            val layer = rnd.nextInt(m.n)
            m.apply(s, com.threecode.cube99.solver.Move(axis, layer, 1 + rnd.nextInt(3)))
        }
        return s
    }

    private fun run(n: Int, seed: Long, moves: Int) {
        val r = Reducer(n)
        val rnd = Random(seed)
        val s = scramble(r.model, rnd, moves)
        val t0 = System.currentTimeMillis()
        var lastStage = ""
        val sol = r.solve(s) { stage, p -> if (stage != lastStage) { lastStage = stage; println("  [$n/$seed] $stage @${System.currentTimeMillis() - t0}ms") } }
        val chk = s.copyOf()
        // normalisasi sama seperti solver: warna -> sisi via center
        val norm = r.normalize(chk)
        r.model.applyAll(norm, sol)
        assertTrue("n=$n seed=$seed tidak selesai", r.model.isSolved(norm))
        println("RES n=$n seed=$seed moves=${sol.size} time=${System.currentTimeMillis() - t0}ms")
    }

    @Test fun solve5() { for (seed in 1L..30L) run(5, seed, 150) }
    @Test fun solve7() { for (seed in 1L..20L) run(7, seed, 250) }
    @Test fun solve9() { for (seed in 1L..40L) run(9, seed, 400) }
    @Test fun solved9() { run(9, 1, 0) }
    @Test fun fewMoves9() { run(9, 5, 3); run(9, 6, 12) }
}
