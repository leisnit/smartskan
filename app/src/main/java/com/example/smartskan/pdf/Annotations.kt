package com.example.smartskan.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.smartskan.data.Signature

/**
 * Annotations live in PDF page coordinates (points, 1/72 inch, origin top-left), so the same data
 * draws on screen (scaled) and straight onto the exported PDF page (scale = 1).
 */
sealed interface PdfAnnotation {
    val id: Long
}

data class InkAnnotation(
    override val id: Long,
    val points: List<Offset>,
    val color: Int,
    val width: Float,
    val highlighter: Boolean,
) : PdfAnnotation

data class TextAnnotation(
    override val id: Long,
    val text: String,
    val topLeft: Offset,
    val fontSize: Float,
    val color: Int,
) : PdfAnnotation

data class SignatureAnnotation(
    override val id: Long,
    val signature: Signature,
    val topLeft: Offset,
    val width: Float,
    val color: Int,
) : PdfAnnotation

/** Movable/resizable items (ink strokes are fixed; use undo to remove them). */
fun PdfAnnotation.isMovable() = this !is InkAnnotation

fun PdfAnnotation.moved(delta: Offset): PdfAnnotation = when (this) {
    is InkAnnotation -> copy(points = points.map { it + delta })
    is TextAnnotation -> copy(topLeft = topLeft + delta)
    is SignatureAnnotation -> copy(topLeft = topLeft + delta)
}

/** Resizes by dragging the bottom-right corner horizontally by [dx] points. */
fun PdfAnnotation.resized(dx: Float): PdfAnnotation = when (this) {
    is InkAnnotation -> this
    is SignatureAnnotation -> copy(width = (width + dx).coerceIn(30f, 1500f))
    is TextAnnotation -> {
        val w = AnnotationRenderer.bounds(this).width.coerceAtLeast(1f)
        copy(fontSize = (fontSize * (w + dx) / w).coerceIn(6f, 144f))
    }
}

object AnnotationRenderer {
    private const val LINE_HEIGHT = 1.25f

    fun bounds(a: PdfAnnotation): Rect = when (a) {
        is InkAnnotation -> {
            val r = a.width / 2
            Rect(
                a.points.minOf { it.x } - r, a.points.minOf { it.y } - r,
                a.points.maxOf { it.x } + r, a.points.maxOf { it.y } + r,
            )
        }
        is TextAnnotation -> {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = a.fontSize }
            val lines = a.text.split('\n')
            val w = lines.maxOf { textPaint.measureText(it) }
            Rect(a.topLeft, androidx.compose.ui.geometry.Size(w, lines.size * a.fontSize * LINE_HEIGHT))
        }
        is SignatureAnnotation ->
            Rect(a.topLeft, androidx.compose.ui.geometry.Size(a.width, a.width * a.signature.aspect))
    }

    /** Draws [items] onto an Android canvas; [scale] converts points to canvas pixels. */
    fun draw(canvas: Canvas, items: List<PdfAnnotation>, scale: Float) {
        items.forEach { draw(canvas, it, scale) }
    }

    fun draw(canvas: Canvas, a: PdfAnnotation, scale: Float) {
        when (a) {
            is InkAnnotation -> {
                val paint = strokePaint(a.color, a.width * scale, round = !a.highlighter)
                if (a.points.size == 1) {
                    canvas.drawPoint(a.points[0].x * scale, a.points[0].y * scale, paint)
                } else {
                    canvas.drawPath(smoothPath(a.points.map { it * scale }), paint)
                }
            }
            is TextAnnotation -> {
                // Local paint: draw() also runs on the export thread.
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                textPaint.color = a.color
                textPaint.textSize = a.fontSize * scale
                val ascent = -textPaint.fontMetrics.ascent
                a.text.split('\n').forEachIndexed { i, line ->
                    val y = (a.topLeft.y + i * a.fontSize * LINE_HEIGHT) * scale + ascent
                    canvas.drawText(line, a.topLeft.x * scale, y, textPaint)
                }
            }
            is SignatureAnnotation -> {
                val w = a.width * scale
                val ox = a.topLeft.x * scale
                val oy = a.topLeft.y * scale
                val paint = strokePaint(a.color, (a.signature.strokeWidth * w).coerceAtLeast(0.8f), round = true)
                a.signature.strokes.forEach { s ->
                    val pts = (0 until s.size / 2).map { Offset(ox + s[it * 2] * w, oy + s[it * 2 + 1] * w) }
                    if (pts.size == 1) canvas.drawPoint(pts[0].x, pts[0].y, paint)
                    else if (pts.isNotEmpty()) canvas.drawPath(smoothPath(pts), paint)
                }
            }
        }
    }

    private fun strokePaint(color: Int, width: Float, round: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.SQUARE
    }

    /** Quadratic smoothing through the midpoints, so finger strokes look like ink, not polylines. */
    fun smoothPath(points: List<Offset>): Path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val mid = (prev + points[i]) / 2f
            quadTo(prev.x, prev.y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }
}
