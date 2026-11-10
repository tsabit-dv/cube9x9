package com.threecode.cube99

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Editor satu sisi n x n. Ketuk atau seret untuk mengecat stiker. */
class FaceGridView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var n = 9
    var facelets = IntArray(6 * 81)
    var face = 0
    var onPaint: ((row: Int, col: Int) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF222222.toInt(); strokeWidth = 2f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        val cell = width.toFloat() / n
        for (r in 0 until n) for (c in 0 until n) {
            rect.set(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell)
            val color = facelets[face * n * n + r * n + c]
            fill.color = color
            canvas.drawRect(rect, fill)
            canvas.drawRect(rect, stroke)
            if (r == n / 2 && c == n / 2) {
                dot.color = if (Palette.isLight(color)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.12f, dot)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val cell = width.toFloat() / n
                val c = (event.x / cell).toInt()
                val r = (event.y / cell).toInt()
                if (r in 0 until n && c in 0 until n) onPaint?.invoke(r, c)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
