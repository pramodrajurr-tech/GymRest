package com.gymrest.timer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** A big countdown ring with the time in the middle. */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var fraction = 1f
    private var label = "3:00"
    private var caption = ""

    private val rect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = 0.04f
    }

    /**
     * @param fraction 1.0 = full ring, 0.0 = empty
     * @param arcColor colour of the moving arc
     * @param textColor colour of the time; the track and caption are a faded version of it
     */
    fun show(fraction: Float, label: String, caption: String, arcColor: Int, textColor: Int) {
        this.fraction = fraction.coerceIn(0f, 1f)
        this.label = label
        this.caption = caption
        arcPaint.color = arcColor
        labelPaint.color = textColor
        trackPaint.color = Color.argb(60, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
        captionPaint.color = Color.argb(190, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = minOf(w, h)
        val stroke = d * 0.06f
        val cx = w / 2f
        val cy = h / 2f
        val r = d / 2f - stroke

        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke

        canvas.drawCircle(cx, cy, r, trackPaint)
        if (fraction > 0f) {
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, -90f, 360f * fraction, false, arcPaint)
        }

        labelPaint.textSize = d * 0.27f
        val fm = labelPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f - d * 0.02f
        canvas.drawText(label, cx, baseline, labelPaint)

        captionPaint.textSize = d * 0.062f
        canvas.drawText(caption, cx, cy + d * 0.20f, captionPaint)
    }
}
