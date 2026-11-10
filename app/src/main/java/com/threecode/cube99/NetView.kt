package com.threecode.cube99

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Jaring kubus (U atas; L F R B tengah; D bawah). Warna per facelet dalam ARGB. */
class NetView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var n = 9
    var facelets = IntArray(6 * 81)
    var highlight: BooleanArray? = null
    var selectedFace = -1
    var onFaceTap: ((Int) -> Unit)? = null

    private val faceCol = intArrayOf(1, 2, 1, 1, 0, 3)
    private val faceRow = intArrayOf(0, 1, 1, 2, 1, 1)
    private val gap = 0.6f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x66000000 }
    private val dim = Paint().apply { color = 0x90202020.toInt() }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFF00FF.toInt() }
    private val sel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF2979FF.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val rect = RectF()

    private val unitsW get() = 4 * n + 3 * gap
    private val unitsH get() = 3 * n + 2 * gap

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * unitsH / unitsW).toInt())
    }

    private fun cell() = min(width / unitsW, height / unitsH)

    private fun faceOrigin(f: Int, cell: Float): Pair<Float, Float> =
        Pair(faceCol[f] * (n + gap) * cell, faceRow[f] * (n + gap) * cell)

    override fun onDraw(canvas: Canvas) {
        val cell = cell()
        val hl = highlight
        mark.strokeWidth = maxOf(2f, cell * 0.18f)
        sel.strokeWidth = maxOf(3f, cell * 0.3f)
        text.textSize = cell * 0.9f
        for (f in 0 until 6) {
            val (ox, oy) = faceOrigin(f, cell)
            for (r in 0 until n) for (c in 0 until n) {
                val i = f * n * n + r * n + c
                rect.set(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell)
                fill.color = facelets[i]
                canvas.drawRect(rect, fill)
                canvas.drawRect(rect, stroke)
                if (hl != null && !hl[i]) canvas.drawRect(rect, dim)
            }
            val mid = n / 2
            text.color = if (Palette.isLight(facelets[f * n * n + mid * n + mid])) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawText("URFDLB"[f].toString(), ox + (mid + 0.5f) * cell, oy + (mid + 0.5f) * cell + cell * 0.32f, text)
        }
        if (hl != null) {
            for (f in 0 until 6) {
                val (ox, oy) = faceOrigin(f, cell)
                for (r in 0 until n) for (c in 0 until n) {
                    if (!hl[f * n * n + r * n + c]) continue
                    rect.set(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell)
                    canvas.drawRect(rect, mark)
                }
            }
        }
        if (selectedFace in 0..5) {
            val (ox, oy) = faceOrigin(selectedFace, cell)
            rect.set(ox, oy, ox + n * cell, oy + n * cell)
            canvas.drawRect(rect, sel)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onFaceTap == null) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val cell = cell()
                for (f in 0 until 6) {
                    val (ox, oy) = faceOrigin(f, cell)
                    if (event.x >= ox && event.x <= ox + n * cell && event.y >= oy && event.y <= oy + n * cell) {
                        onFaceTap?.invoke(f)
                        performClick()
                        return true
                    }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
