package com.threecode.cube99

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.threecode.cube99.databinding.ActivitySolveBinding
import com.threecode.cube99.solver.CubeModel
import com.threecode.cube99.solver.Move
import com.threecode.cube99.solver.Reducer

class SolveActivity : AppCompatActivity() {
    private lateinit var b: ActivitySolveBinding
    private lateinit var storage: Storage
    private val n = 9
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var reducer: Reducer
    private lateinit var model: CubeModel
    private lateinit var moves: List<Move>
    private lateinit var startState: IntArray
    private lateinit var state: IntArray
    private lateinit var faceToPaint: IntArray
    private val argb = IntArray(6 * n * n)
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySolveBinding.inflate(layoutInflater)
        setContentView(b.root)
        storage = Storage(this)
        b.btnBack.setOnClickListener { finish() }
        b.netView.n = n

        val colors = storage.loadSession(6 * n * n)
        if (colors == null) {
            showError("Tidak ada data kubus. Kembali dan isi dulu.")
            return
        }
        Thread { solve(colors) }.start()
    }

    private fun solve(colors: IntArray) {
        try {
            val r = Reducer(n)
            val result = r.solve(colors) { stage, p ->
                ui.post {
                    b.loadText.text = stage
                    b.loadBar.progress = (p * 100).toInt()
                }
            }
            val norm = r.normalize(colors)
            ui.post { onSolved(r, result, norm, colors) }
        } catch (e: Throwable) {
            val msg = when (e) {
                is OutOfMemoryError -> "Memori habis saat menghitung."
                else -> e.message ?: e.toString()
            }
            ui.post { showError("Gagal: $msg\n\nBiasanya ada stiker yang salah isi atau arah pandang sisi tertukar. Periksa lagi input.") }
        }
    }

    private fun showError(msg: String) {
        b.loadBar.visibility = View.GONE
        b.loadText.text = msg
        b.btnBack.visibility = View.VISIBLE
    }

    private fun onSolved(r: Reducer, result: List<Move>, norm: IntArray, colors: IntArray) {
        reducer = r
        model = r.model
        moves = result
        startState = norm
        faceToPaint = IntArray(6) { colors[model.idx(it, model.mid, model.mid)] }
        state = startState.copyOf()
        step = storage.step.coerceIn(0, moves.size)
        repeat(step) { model.apply(state, moves[it]) }

        b.loading.visibility = View.GONE
        b.content.visibility = View.VISIBLE
        b.stepBar.max = maxOf(1, moves.size)
        b.btnNext.setOnClickListener { go(1) }
        b.btnPrev.setOnClickListener { go(-1) }
        b.btnJump.setOnClickListener { jump() }
        b.btnNotation.setOnClickListener { showNotation() }
        render()
    }

    private fun go(delta: Int) {
        if (delta > 0 && step < moves.size) {
            model.apply(state, moves[step]); step++
        } else if (delta < 0 && step > 0) {
            step--; model.apply(state, moves[step].inverse())
        } else return
        storage.step = step
        render()
    }

    private fun jump() {
        val target = b.jumpInput.text.toString().toIntOrNull()
        if (target == null || target < 0 || target > moves.size) {
            b.jumpInput.error = "0 sampai ${moves.size}"
            return
        }
        state = startState.copyOf()
        repeat(target) { model.apply(state, moves[it]) }
        step = target
        storage.step = step
        b.jumpInput.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(b.jumpInput.windowToken, 0)
        render()
    }

    private fun render() {
        for (i in argb.indices) argb[i] = Palette.colors[faceToPaint[state[i]]]
        b.netView.facelets = argb
        val total = moves.size
        b.stepBar.progress = step
        b.stepText.text = "Langkah $step dari $total"
        b.stageText.text = when (reducer.stageOf(state)) {
            0 -> "Tahap 1: menyusun center"
            1 -> "Tahap 2: memasangkan edge"
            2 -> "Tahap 3: menyelesaikan seperti kubus 3x3"
            else -> "Selesai"
        }
        if (step < total) {
            val m = moves[step]
            val perm = model.perm(m)
            b.netView.highlight = BooleanArray(perm.size) { perm[it] != it }
            b.moveText.text = model.notation(m)
            b.moveDesc.text = model.explain(m)
            val upcoming = (step + 1 until minOf(total, step + 6)).joinToString("   ") { model.notation(moves[it]) }
            b.nextText.text = if (upcoming.isEmpty()) "Ini langkah terakhir" else "Berikutnya: $upcoming"
        } else {
            b.netView.highlight = null
            b.moveText.text = if (model.isSolved(state)) "Selesai!" else "Selesai (cek kubus)"
            b.moveDesc.text = "Semua langkah sudah dijalankan."
            b.nextText.text = ""
        }
        b.btnNext.isEnabled = step < total
        b.btnPrev.isEnabled = step > 0
        b.netView.invalidate()
    }

    private fun showNotation() {
        AlertDialog.Builder(this)
            .setTitle("Cara membaca langkah")
            .setMessage(
                "Huruf = sisi: U atas, D bawah, L kiri, R kanan, F depan, B belakang.\n\n" +
                    "Tanpa angka (R) = lapisan paling luar sisi itu.\n" +
                    "Angka di depan (3R) = hanya lapisan ke-3 dihitung dari sisi itu.\n\n" +
                    "Tanpa tanda = searah jarum jam 90°, dilihat dari sisi itu.\n" +
                    "Tanda ' (R') = berlawanan jarum jam.\n" +
                    "Angka 2 di belakang (R2) = 180°.\n\n" +
                    "Pada jaring, kotak yang berbingkai magenta adalah stiker yang ikut berputar; sisanya digelapkan. " +
                    "Jaring selalu menunjukkan keadaan kubus yang seharusnya sebelum langkah ini."
            )
            .setPositiveButton("Mengerti", null).show()
    }
}
