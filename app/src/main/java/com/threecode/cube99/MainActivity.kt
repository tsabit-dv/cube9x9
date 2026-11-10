package com.threecode.cube99

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.threecode.cube99.databinding.ActivityMainBinding
import com.threecode.cube99.solver.CubeModel
import com.threecode.cube99.solver.Move
import java.util.Random

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var storage: Storage
    private val n = 9
    private val model = CubeModel(n)
    private lateinit var colors: IntArray   // indeks palet 0..5 per facelet
    private var face = 0
    private var paint = 0
    private val argb = IntArray(model.size)
    private val swatches = ArrayList<View>()
    private val countViews = ArrayList<TextView>()

    private val hints = arrayOf(
        "Lihat dari atas. Tepi ATAS gambar = arah sisi Belakang (B).",
        "Lihat dari kanan, sisi Atas (U) berada di atas gambar.",
        "Lihat dari depan, sisi Atas (U) berada di atas gambar.",
        "Lihat dari bawah. Tepi ATAS gambar = arah sisi Depan (F).",
        "Lihat dari kiri, sisi Atas (U) berada di atas gambar.",
        "Lihat dari belakang, sisi Atas (U) berada di atas gambar.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        storage = Storage(this)
        colors = storage.loadInput(model.size) ?: solvedColors()

        b.netView.n = n
        b.netView.onFaceTap = { selectFace(it) }
        b.gridView.n = n
        b.gridView.onPaint = { r, c ->
            val i = model.idx(face, r, c)
            if (colors[i] != paint) {
                colors[i] = paint
                refresh()
            }
        }
        buildPalette()
        buildCounts()
        b.btnSolve.setOnClickListener { startSolve() }
        b.btnResume.setOnClickListener { startActivity(Intent(this, SolveActivity::class.java)) }
        b.btnReset.setOnClickListener {
            AlertDialog.Builder(this).setMessage("Kembalikan semua stiker ke kubus selesai?")
                .setPositiveButton("Ya") { _, _ -> colors = solvedColors(); refresh() }
                .setNegativeButton("Batal", null).show()
        }
        b.btnRandom.setOnClickListener {
            AlertDialog.Builder(this).setMessage("Isi dengan kubus acak untuk mencoba aplikasi? Input sekarang akan hilang.")
                .setPositiveButton("Ya") { _, _ -> colors = randomColors(); refresh() }
                .setNegativeButton("Batal", null).show()
        }
        b.btnHelp.setOnClickListener { showHelp() }
        selectFace(0)
    }

    override fun onResume() {
        super.onResume()
        val session = storage.loadSession(model.size)
        b.btnResume.visibility = if (session != null && storage.step > 0) View.VISIBLE else View.GONE
        if (session != null && storage.step > 0) b.btnResume.text = "Lanjutkan penyelesaian (langkah ${storage.step})"
    }

    private fun solvedColors() = IntArray(model.size) { Palette.defaultFaceColor[it / (n * n)] }

    private fun randomColors(): IntArray {
        val s = model.solvedState()
        val rnd = Random()
        repeat(400) {
            var layer = rnd.nextInt(n)
            if (layer == model.mid) layer = 0
            model.apply(s, Move(rnd.nextInt(3), layer, 1 + rnd.nextInt(3)))
        }
        return IntArray(model.size) { Palette.defaultFaceColor[s[it]] }
    }

    private fun buildPalette() {
        val dp = resources.displayMetrics.density
        for (i in 0 until 6) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(0, (46 * dp).toInt(), 1f)
            lp.marginEnd = (4 * dp).toInt()
            v.layoutParams = lp
            v.contentDescription = Palette.names[i]
            v.setOnClickListener { paint = i; updatePalette() }
            b.palette.addView(v)
            swatches.add(v)
        }
        updatePalette()
    }

    private fun updatePalette() {
        val dp = resources.displayMetrics.density
        for (i in 0 until 6) {
            val d = GradientDrawable()
            d.setColor(Palette.colors[i])
            d.cornerRadius = 8 * dp
            d.setStroke(((if (i == paint) 4 else 1) * dp).toInt(), if (i == paint) 0xFF2979FF.toInt() else 0xFF666666.toInt())
            swatches[i].background = d
        }
    }

    private fun buildCounts() {
        val dp = resources.displayMetrics.density
        for (i in 0 until 6) {
            val t = TextView(this)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = (4 * dp).toInt()
            t.layoutParams = lp
            t.gravity = android.view.Gravity.CENTER
            t.textSize = 12f
            t.setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            val d = GradientDrawable()
            d.setColor(Palette.colors[i])
            d.cornerRadius = 6 * dp
            t.background = d
            b.counts.addView(t)
            countViews.add(t)
        }
    }

    private fun selectFace(f: Int) {
        face = f
        b.netView.selectedFace = f
        b.gridView.face = f
        b.faceTitle.text = "Sisi ${CubeModel.FACE_NAMES[f]}"
        b.faceHint.text = hints[f]
        refresh()
    }

    private fun refresh() {
        for (i in argb.indices) argb[i] = Palette.colors[colors[i]]
        b.netView.facelets = argb
        b.gridView.facelets = argb
        b.netView.invalidate()
        b.gridView.invalidate()
        storage.saveInput(colors)

        val counts = IntArray(6)
        for (c in colors) counts[c]++
        val per = n * n
        var ok = true
        for (i in 0 until 6) {
            val t = countViews[i]
            t.text = if (counts[i] == per) "$per" else "${counts[i]}/$per"
            t.setTextColor(if (counts[i] == per) (if (Palette.isLight(Palette.colors[i])) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()) else 0xFFFF1744.toInt())
            t.paint.isFakeBoldText = counts[i] != per
            if (counts[i] != per) ok = false
        }
        val centers = HashSet<Int>()
        for (f in 0 until 6) centers.add(colors[model.idx(f, model.mid, model.mid)])
        b.statusText.text = when {
            !ok -> "Tiap warna harus tepat $per stiker. Periksa angka merah di atas."
            centers.size != 6 -> "Warna center (titik di tengah tiap sisi) harus 6 warna berbeda."
            else -> "Jumlah warna sudah benar. Siap diselesaikan."
        }
        b.btnSolve.isEnabled = ok && centers.size == 6
    }

    private fun startSolve() {
        storage.startSession(colors)
        startActivity(Intent(this, SolveActivity::class.java))
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Cara mengisi")
            .setMessage(
                "1. Pegang kubus dengan satu sisi menghadap atas (U) dan satu sisi menghadap depan (F). " +
                    "Tetap konsisten sampai semua sisi terisi.\n\n" +
                    "2. Pilih sisi di jaring, lalu cat 81 stiker sesuai arah pandang yang tertulis di bawah judul sisi. " +
                    "Seret jari untuk mengecat beberapa stiker sekaligus.\n\n" +
                    "3. Titik di tengah tiap sisi adalah center tetap. Warnanya menentukan warna sisi itu.\n\n" +
                    "4. Tiap warna harus tepat 81 stiker. Kalau solver menolak, biasanya ada stiker yang salah isi " +
                    "atau arah pandang sisi tertukar.\n\n" +
                    "Solusi memakai ribuan langkah. Langkah terakhir tersimpan, jadi bisa dilanjutkan nanti."
            )
            .setPositiveButton("Mengerti", null).show()
    }
}
