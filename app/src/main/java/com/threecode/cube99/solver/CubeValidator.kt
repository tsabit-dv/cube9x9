package com.threecode.cube99.solver

/**
 * Pemeriksa legalitas kubus dari stiker. Input: state berisi indeks sisi (hasil [Reducer.normalize]).
 * Memeriksa identitas tiap potongan fisik terhadap kubus selesai, jadi stiker yang salah isi
 * ditunjuk lokasinya, bukan ditebak atau diabaikan.
 *
 * Yang diperiksa: sudut (8 unik, kiralitas, total putaran kelipatan 3), edge tengah (12 unik,
 * total flip genap), dan tiap wing (24 unik per orbit). Paritas permutasi antar-potongan
 * sengaja tidak dilarang: pada kubus besar paritas itu dikoreksi solver lewat gerakan slab.
 */
class CubeValidator(private val model: CubeModel, private val reg: Regions, private val wings: WingParity) {
    private var cornerParity = 0
    private var edgeParity = 0

    /** Paritas (0/1) permutasi yang memetakan kunci posisi ke kunci rumah. */
    private fun permutationParity(dest: Map<Int, Int>): Int {
        val seen = HashSet<Int>()
        var parity = 0
        for (k in dest.keys) {
            if (!seen.add(k)) continue
            var len = 1
            var j = dest.getValue(k)
            while (j != k) { seen.add(j); j = dest.getValue(j); len++ }
            parity = parity xor ((len - 1) and 1)
        }
        return parity
    }

    /** Daftar masalah; kosong berarti kubus mungkin dicapai dari kubus selesai. */
    fun validate(state: IntArray): List<String> {
        val problems = ArrayList<String>()
        problems.addAll(checkCorners(state))
        problems.addAll(checkMiddleEdges(state))
        if (problems.isEmpty() && cornerParity != edgeParity) {
            problems.add("Paritas permutasi sudut dan edge tengah tidak cocok: bagian 3x3 tidak mungkin diselesaikan. Biasanya dua stiker sudut atau edge tengah tertukar.")
        }
        if (problems.isEmpty()) problems.addAll(wings.checkIdentity(state))
        return problems
    }

    private fun groupByCubie(pred: BooleanArray): Map<Int, List<Int>> =
        (0 until model.size).filter { pred[it] }.groupBy { model.cubieKey(it) }.mapValues { it.value.sorted() }

    private fun normalOf(face: Int) = when (face) {
        CubeModel.U -> intArrayOf(0, 1, 0)
        CubeModel.R -> intArrayOf(1, 0, 0)
        CubeModel.F -> intArrayOf(0, 0, 1)
        CubeModel.D -> intArrayOf(0, -1, 0)
        CubeModel.L -> intArrayOf(-1, 0, 0)
        else -> intArrayOf(0, 0, -1)
    }

    /** Urutan facelet sudut searah jarum jam dilihat dari luar, dimulai dari facelet di sisi U atau D. */
    private fun clockwise(fs: List<Int>): List<Int> {
        val first = fs.first { model.faceOf(it) == CubeModel.U || model.faceOf(it) == CubeModel.D }
        val rest = fs.filter { it != first }
        val a = normalOf(model.faceOf(first)); val b = normalOf(model.faceOf(rest[0])); val c = normalOf(model.faceOf(rest[1]))
        val det = a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0])
        return if (det > 0) listOf(first, rest[0], rest[1]) else listOf(first, rest[1], rest[0])
    }

    /** Prioritas sisi untuk orientasi edge: U/D, lalu F/B, lalu R/L. */
    private fun priority(face: Int) = when (face) { CubeModel.U, CubeModel.D -> 0; CubeModel.F, CubeModel.B -> 1; else -> 2 }

    private fun checkCorners(state: IntArray): List<String> {
        val problems = ArrayList<String>()
        val corners = groupByCubie(reg.corner)
        // kunci rumah: himpunan warna terurut
        val home = HashMap<Int, List<Int>>()   // mask warna -> daftar warna searah jarum jam dari solved
        val homeKey = HashMap<Int, Int>()      // mask warna -> kunci kubus rumah
        for ((key, fs) in corners) {
            val cw = clockwise(fs)
            home[mask(cw.map { model.faceOf(it) })] = cw.map { model.faceOf(it) }
            homeKey[mask(cw.map { model.faceOf(it) })] = key
        }
        val dest = HashMap<Int, Int>()
        val seen = HashMap<Int, String>()
        var twist = 0
        for ((key, fs) in corners) {
            val cw = clockwise(fs)
            val colors = cw.map { state[it] }
            val where = fs.joinToString("+") { model.describeFacelet(it) }
            val m = mask(colors)
            val ref = home[m]
            if (ref == null || colors.toSet().size != 3) {
                problems.add("Sudut di $where berwarna mustahil (${colors.joinToString("") { CubeModel.FACE_LETTERS[it].toString() }}). Cek stiker sudut itu.")
                continue
            }
            val prev = seen.put(m, where)
            if (prev != null) problems.add("Sudut di $prev dan $where berwarna sama. Kubus asli hanya punya satu sudut seperti itu.")
            dest[key] = homeKey.getValue(m)
            val r = (0 until 3).firstOrNull { rot -> (0 until 3).all { colors[it] == ref[(it + rot) % 3] } }
            if (r == null) problems.add("Sudut di $where terbalik/kiral salah (${colors.joinToString("") { CubeModel.FACE_LETTERS[it].toString() }}). Biasanya dua stiker sudut tertukar.")
            else twist += r
        }
        if (problems.isEmpty() && twist % 3 != 0) problems.add("Satu sudut terpuntir sendirian (total putaran sudut tidak kelipatan 3). Cek warna stiker sudut.")
        if (problems.isEmpty()) cornerParity = permutationParity(dest)
        return problems
    }

    private fun checkMiddleEdges(state: IntArray): List<String> {
        val problems = ArrayList<String>()
        val edges = groupByCubie(reg.middleEdge)
        val home = HashMap<Int, List<Int>>()
        val ordered = edges.mapValues { (_, fs) -> fs.sortedBy { priority(model.faceOf(it)) } }
        val homeKey = HashMap<Int, Int>()
        val dest = HashMap<Int, Int>()
        for ((key, fs) in ordered) { home[mask(fs.map { model.faceOf(it) })] = fs.map { model.faceOf(it) }; homeKey[mask(fs.map { model.faceOf(it) })] = key }
        val seen = HashMap<Int, String>()
        var flips = 0
        for ((key, fs) in ordered) {
            val colors = fs.map { state[it] }
            val where = fs.joinToString("+") { model.describeFacelet(it) }
            val m = mask(colors)
            val ref = home[m]
            if (ref == null || colors.toSet().size != 2) {
                problems.add("Edge tengah di $where berwarna mustahil (${colors.joinToString("") { CubeModel.FACE_LETTERS[it].toString() }}). Cek dua stiker itu.")
                continue
            }
            val prev = seen.put(m, where)
            if (prev != null) problems.add("Edge tengah di $prev dan $where berwarna sama. Kubus asli hanya punya satu edge seperti itu.")
            if (colors[0] != ref[0]) flips++
            dest[key] = homeKey.getValue(m)
        }
        if (problems.isEmpty() && flips % 2 != 0) problems.add("Satu edge tengah terbalik sendirian (total flip ganjil). Cek stiker edge tengah.")
        if (problems.isEmpty()) edgeParity = permutationParity(dest)
        return problems
    }

    private fun mask(colors: List<Int>): Int = colors.fold(0) { acc, c -> acc or (1 shl c) }
}
