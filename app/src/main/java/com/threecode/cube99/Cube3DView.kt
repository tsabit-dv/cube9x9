package com.threecode.cube99

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.threecode.cube99.solver.CubeModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Editor kubus 3D. Seret untuk memutar model (atau untuk mengecat bila [paintOnDrag] aktif);
 * ketuk satu stiker untuk mengecatnya. Hanya stiker yang menghadap layar yang digambar dan bisa diketuk.
 */
class Cube3DView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var n = 9
        set(v) { field = v; model = CubeModel(v); rebuild() }
    var facelets = IntArray(6 * 81)
    var paintOnDrag = false
    var onPaint: ((index: Int) -> Unit)? = null

    private var model = CubeModel(9)
    private var yaw = Math.toRadians(-35.0)
    private var pitch = Math.toRadians(25.0)

    // per facelet: pusat (x,y,z) dan dua sumbu tepi, dalam satuan kubus, terpusat di titik asal
    private var cen = FloatArray(0)
    private var nrm = FloatArray(0)
    private var axU = FloatArray(0)
    private var axV = FloatArray(0)

    // hasil proyeksi frame terakhir
    private var quad = FloatArray(6 * 81 * 8)
    private var visible = BooleanArray(6 * 81)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF111111.toInt() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val path = Path()

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init { rebuild() }

    private fun rebuild() {
        val size = model.size
        quad = FloatArray(size * 8); visible = BooleanArray(size)
        cen = FloatArray(size * 3); nrm = FloatArray(size * 3)
        axU = FloatArray(size * 3); axV = FloatArray(size * 3)
        val h = (model.n - 1) / 2f
        for (i in 0 until size) {
            val f = model.faceOf(i)
            val (nx, ny, nz) = NORMALS[f]
            cen[i * 3] = model.coordX(i) - h + nx * 0.5f
            cen[i * 3 + 1] = model.coordY(i) - h + ny * 0.5f
            cen[i * 3 + 2] = model.coordZ(i) - h + nz * 0.5f
            nrm[i * 3] = nx; nrm[i * 3 + 1] = ny; nrm[i * 3 + 2] = nz
        }
        // face U/D: u = x, v = z. face F/B: u = x, v = y. face R/L: u = z, v = y.
        for (i in 0 until size) {
            when (model.faceOf(i)) {
                CubeModel.U, CubeModel.D -> { set3(axU, i, 1f, 0f, 0f); set3(axV, i, 0f, 0f, 1f) }
                CubeModel.F, CubeModel.B -> { set3(axU, i, 1f, 0f, 0f); set3(axV, i, 0f, 1f, 0f) }
                else -> { set3(axU, i, 0f, 0f, 1f); set3(axV, i, 0f, 1f, 0f) }
            }
        }
    }

    private fun set3(a: FloatArray, i: Int, x: Float, y: Float, z: Float) { a[i * 3] = x; a[i * 3 + 1] = y; a[i * 3 + 2] = z }

    /** Putar model supaya [face] menghadap layar. */
    fun lookAt(face: Int) {
        val (y, p) = when (face) {
            CubeModel.U -> Pair(0.0, 75.0)
            CubeModel.D -> Pair(0.0, -75.0)
            CubeModel.F -> Pair(0.0, 15.0)
            CubeModel.R -> Pair(-90.0, 15.0)
            CubeModel.B -> Pair(180.0, 15.0)
            else -> Pair(90.0, 15.0)
        }
        yaw = Math.toRadians(y); pitch = Math.toRadians(p)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    /** Hitung proyeksi semua stiker; mengisi [quad] dan [visible]. */
    private fun project() {
        val scale = min(width, height) / (model.n * 1.9f)
        val cx = width / 2f
        val cy = height / 2f
        val cyaw = cos(yaw).toFloat(); val syaw = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
        // rotasi: yaw sekitar y, lalu pitch sekitar x. Hasil: layar x kanan, y bawah, z ke arah penonton.
        fun rx(x: Float, y: Float, z: Float) = x * cyaw + z * syaw
        fun ry(x: Float, y: Float, z: Float): Float {
            val y1 = y
            val z1 = -x * syaw + z * cyaw
            return y1 * cp - z1 * sp
        }
        fun rz(x: Float, y: Float, z: Float): Float {
            val z1 = -x * syaw + z * cyaw
            return y * sp + z1 * cp
        }
        for (i in 0 until model.size) {
            val nx = nrm[i * 3]; val ny = nrm[i * 3 + 1]; val nz = nrm[i * 3 + 2]
            val vis = rz(nx, ny, nz) > 0.02f
            visible[i] = vis
            if (!vis) continue
            val ux = axU[i * 3] * 0.5f; val uy = axU[i * 3 + 1] * 0.5f; val uz = axU[i * 3 + 2] * 0.5f
            val vx = axV[i * 3] * 0.5f; val vy = axV[i * 3 + 1] * 0.5f; val vz = axV[i * 3 + 2] * 0.5f
            val px = cen[i * 3]; val py = cen[i * 3 + 1]; val pz = cen[i * 3 + 2]
            for (k in 0 until 4) {
                val su = if (k == 0 || k == 3) -1f else 1f
                val sv = if (k < 2) -1f else 1f
                val x = px + su * ux + sv * vx
                val y = py + su * uy + sv * vy
                val z = pz + su * uz + sv * vz
                quad[i * 8 + k * 2] = cx + rx(x, y, z) * scale
                quad[i * 8 + k * 2 + 1] = cy - ry(x, y, z) * scale
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        project()
        val scale = min(width, height) / (model.n * 1.9f)
        stroke.strokeWidth = maxOf(1f, scale * 0.06f)
        for (i in 0 until model.size) {
            if (!visible[i]) continue
            path.rewind()
            path.moveTo(quad[i * 8], quad[i * 8 + 1])
            for (k in 1 until 4) path.lineTo(quad[i * 8 + k * 2], quad[i * 8 + k * 2 + 1])
            path.close()
            fill.color = facelets[i]
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
            val r = model.rowOf(i); val c = model.colOf(i)
            if (r == model.mid && c == model.mid) {
                val mx = (quad[i * 8] + quad[i * 8 + 4]) / 2f
                val my = (quad[i * 8 + 1] + quad[i * 8 + 5]) / 2f
                label.color = if (Palette.isLight(facelets[i])) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                label.textSize = scale * 0.9f
                canvas.drawText("URFDLB"[model.faceOf(i)].toString(), mx, my + scale * 0.3f, label)
            }
        }
    }

    private fun hit(x: Float, y: Float): Int {
        for (i in 0 until model.size) {
            if (!visible[i]) continue
            if (inQuad(i, x, y)) return i
        }
        return -1
    }

    private fun inQuad(i: Int, x: Float, y: Float): Boolean {
        var sign = 0
        for (k in 0 until 4) {
            val x1 = quad[i * 8 + k * 2]; val y1 = quad[i * 8 + k * 2 + 1]
            val x2 = quad[i * 8 + ((k + 1) % 4) * 2]; val y2 = quad[i * 8 + ((k + 1) % 4) * 2 + 1]
            val cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) continue
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                dragging = false
                if (paintOnDrag) paintAt(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (paintOnDrag) {
                    paintAt(event.x, event.y)
                } else {
                    if (!dragging && (abs(event.x - downX) > slop || abs(event.y - downY) > slop)) dragging = true
                    if (dragging) {
                        yaw += Math.toRadians((event.x - lastX) * 0.45)
                        pitch = (pitch + Math.toRadians((event.y - lastY) * 0.45)).coerceIn(-HALF_PI, HALF_PI)
                        invalidate()
                    }
                }
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!paintOnDrag && !dragging) paintAt(event.x, event.y)
                performClick()
                return true
            }
        }
        return true
    }

    private fun paintAt(x: Float, y: Float) {
        val i = hit(x, y)
        if (i >= 0) onPaint?.invoke(i)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        val HALF_PI = Math.PI / 2 - 0.01
        // urutan U R F D L B
        val NORMALS = arrayOf(
            Triple(0f, 1f, 0f), Triple(1f, 0f, 0f), Triple(0f, 0f, 1f),
            Triple(0f, -1f, 0f), Triple(-1f, 0f, 0f), Triple(0f, 0f, -1f),
        )
    }
}
