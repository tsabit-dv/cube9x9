package com.threecode.cube99.solver

import cs.min2phase.Search

class SolveException(message: String) : Exception(message)

/**
 * Solver kubus ganjil n x n x n dengan metode reduksi:
 * 1. center (makro komutator), 2. pairing wing (3-siklus wing), 3. 3x3 (Kociemba).
 */
class Reducer(val n: Int) {
    val model = CubeModel(n)
    private val reg = Regions(model)
    private val setups by lazy { MacroLibrary.outerSetups(model) }
    private val centerSetups by lazy { MacroLibrary.outerSetups(model, 1) }
    private val centerLib by lazy { MacroLibrary(model, centerSetups, MacroLibrary.centerCores(model, reg), reg.center) }
    private val wingParity by lazy { WingParity(model, reg) }
    private val wingLib by lazy {
        val slabSetups = setups + MacroLibrary.innerMoves(model).map { listOf(it) }
        MacroLibrary(model, slabSetups, MacroLibrary.wingCores(model, reg), reg.wing)
    }

    init {
        require(n >= 3 && n % 2 == 1) { "hanya kubus ganjil" }
    }

    /** Ubah warna sembarang jadi indeks sisi berdasarkan warna center tetap. */
    fun normalize(colors: IntArray): IntArray {
        require(colors.size == model.size)
        val colorToFace = HashMap<Int, Int>()
        for (f in 0 until 6) {
            val c = colors[model.idx(f, model.mid, model.mid)]
            if (colorToFace.put(c, f) != null) throw SolveException("Warna center ada yang kembar")
        }
        val counts = HashMap<Int, Int>()
        for (c in colors) counts[c] = (counts[c] ?: 0) + 1
        for ((c, k) in counts) {
            if (!colorToFace.containsKey(c)) throw SolveException("Ada warna yang bukan warna center")
            if (k != model.nn) throw SolveException("Jumlah stiker tiap warna harus ${model.nn}")
        }
        return IntArray(colors.size) { colorToFace.getValue(colors[it]) }
    }

    /**
     * Hasil: daftar gerakan (sudah disederhanakan) yang menyelesaikan [colors].
     * [onStage] dipanggil dengan (nama tahap, kemajuan 0..1 dalam tahap).
     */
    fun solve(colors: IntArray, onStage: (String, Float) -> Unit = { _, _ -> }): List<Move> {
        val start = normalize(colors)
        val all = ArrayList<Move>()
        val state = start.copyOf()

        // 1. center
        onStage("Menyiapkan tabel center", 0f)
        val cLib = centerLib
        val cTarget = IntArray(model.size) { -1 }
        for (i in 0 until model.size) if (reg.center[i]) cTarget[i] = model.faceOf(i)
        fun solveCenters(label: String) {
            val work = state.copyOf()
            val wrong0 = (0 until model.size).count { reg.center[it] && work[it] != cTarget[it] }
            val mv = GreedyStage(model, cLib, cTarget, work).run(5000) { wrong ->
                onStage(label, 1f - wrong.toFloat() / maxOf(1, wrong0))
            } ?: throw SolveException("Solver center macet")
            model.applyAll(state, mv)
            all.addAll(mv)
        }
        solveCenters("Menyusun center")

        // 2. wing
        onStage("Menyiapkan tabel edge", 0f)
        val wTarget = IntArray(model.size) { -1 }
        for (i in 0 until model.size) if (reg.wing[i]) wTarget[i] = state[middleFacelet(i)]
        val parity = wingParity
        val par = parity.parities(state, wTarget)
        var fixed = false
        for (o in 0 until parity.orbitCount) if (par[o] == 1) {
            val mv = parity.fixMoves[o] ?: throw SolveException("Tidak ada gerakan koreksi paritas")
            model.apply(state, mv)
            all.add(mv)
            fixed = true
        }
        if (fixed) {
            solveCenters("Menyusun ulang center")
            if (parity.parities(state, wTarget).any { it != 0 }) throw SolveException("Koreksi paritas gagal")
        }
        val wLib = wingLib
        val wWork = state.copyOf()
        val wingWrong = (0 until model.size).count { reg.wing[it] && wWork[it] != wTarget[it] }
        val wMoves = GreedyStage(model, wLib, wTarget, wWork).run(5000) { wrong ->
            onStage("Memasangkan edge", 1f - wrong.toFloat() / maxOf(1, wingWrong))
        } ?: throw SolveException("Solver edge macet")
        model.applyAll(state, wMoves)
        all.addAll(wMoves)

        // 3. 3x3
        onStage("Menyelesaikan 3x3", 0f)
        val sol = solve3x3(state)
        for (m in sol) model.apply(state, m)
        all.addAll(sol)

        val result = CubeModel.simplify(all)
        val check = start.copyOf()
        model.applyAll(check, result)
        if (!model.isSolved(check)) throw SolveException("Verifikasi akhir gagal (cek input stiker)")
        onStage("Selesai", 1f)
        return result
    }

    /** Tahap state (warna sudah dinormalisasi): 0 center, 1 edge, 2 3x3 akhir, 3 selesai. */
    fun stageOf(state: IntArray): Int {
        if (model.isSolved(state)) return 3
        for (i in 0 until model.size) if (reg.center[i] && state[i] != model.faceOf(i)) return 0
        for (i in 0 until model.size) if (reg.wing[i] && state[i] != state[middleFacelet(i)]) return 1
        return 2
    }

    /** Facelet edge-tengah pada sisi dan slot yang sama dengan wing [i]. */
    private fun middleFacelet(i: Int): Int {
        val f = model.faceOf(i)
        val r = model.rowOf(i)
        val c = model.colOf(i)
        return if (r == 0 || r == n - 1) model.idx(f, r, model.mid) else model.idx(f, model.mid, c)
    }

    private fun solve3x3(state: IntArray): List<Move> {
        val sb = StringBuilder(54)
        val samples = intArrayOf(0, model.mid, n - 1)
        for (f in 0 until 6) for (r in samples) for (c in samples) {
            sb.append(CubeModel.FACE_LETTERS[state[model.idx(f, r, c)]])
        }
        Tools.ensureInit()
        val res = Search().solution(sb.toString(), 24, 5_000_000L, 0, 0)
        if (res.startsWith("Error")) throw SolveException("3x3 gagal: $res")
        return res.trim().split(" ").filter { it.isNotEmpty() }.map { model.parseOuter(it) }
    }

    object Tools {
        @Volatile private var done = false
        fun ensureInit() {
            if (done) return
            synchronized(this) {
                if (!done) { Search.init(); done = true }
            }
        }
    }
}
