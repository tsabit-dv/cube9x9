package com.threecode.cube99

import com.threecode.cube99.solver.CubeModel
import com.threecode.cube99.solver.Move
import cs.min2phase.Search
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ModelTest {
    @Test fun fourQuarterTurnsIdentity() {
        val m = CubeModel(9)
        for (axis in 0..2) for (layer in 0 until 9) {
            val s = m.solvedState()
            repeat(4) { m.apply(s, Move(axis, layer, 1)) }
            assertTrue(m.isSolved(s))
        }
    }

    @Test fun matchesMin2PhaseOn3x3() {
        val m = CubeModel(3)
        Search.init()
        val rnd = Random(1)
        repeat(20) {
            val s = m.solvedState()
            repeat(30) { m.apply(s, m.faceMove(rnd.nextInt(6), 1, 1 + rnd.nextInt(3))) }
            val str = String(CharArray(54) { "URFDLB"[s[it]] })
            val sol = Search().solution(str, 25, 100000, 0, 0)
            assertFalse(sol, sol.startsWith("Error"))
            for (t in sol.trim().split(" ").filter { it.isNotEmpty() }) m.apply(s, m.parseOuter(t))
            assertTrue(m.isSolved(s))
        }
    }

    @Test fun outerTurnOn9x9IsSame3x3Turn() {
        val m = CubeModel(9)
        val s = m.solvedState()
        m.apply(s, m.faceMove(CubeModel.F, 1, 1))
        // F cw: baris bawah U (r=8) jadi warna L
        assertEquals(CubeModel.L, s[m.idx(CubeModel.U, 8, 4)])
        assertEquals(CubeModel.U, s[m.idx(CubeModel.R, 4, 0)])
        assertEquals("F", m.notation(m.faceMove(CubeModel.F, 1, 1)))
        assertEquals("3L'", m.notation(m.faceMove(CubeModel.L, 3, 3)))
        assertEquals("4D2", m.notation(m.faceMove(CubeModel.D, 4, 2)))
    }
}
