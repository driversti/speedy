package dev.seniorjava.speedy.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.SpeedFormatter
import dev.seniorjava.speedy.domain.SpeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Renders the monochromatic status-bar icon. Per spec (section 5):
 *   - Bitmap is ALPHA_8, 96×96 px (matches `status_bar_icon_size` at xxxhdpi;
 *     smaller assets fall back to a blank small icon on modern Pixels).
 *   - A single Bitmap + Canvas is reused across ticks — we CLEAR it, redraw
 *     the new text, and hand the same instance to `Icon.createWithBitmap()`.
 *   - Paint.color = WHITE — the system uses only the alpha channel.
 *   - Text is drawn at [TEXT_SIZE_PX] when it fits, and auto-scaled down
 *     per-line when it doesn't, so 5-character readings like `↑999M` still
 *     fit the unit suffix without the system cropping it.
 */
@Singleton
class SpeedIconRenderer @Inject constructor(
    private val formatter: SpeedFormatter,
) {
    private val bitmap: Bitmap by lazy(NONE) {
        Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ALPHA_8)
    }
    private val canvas: Canvas by lazy(NONE) { Canvas(bitmap) }

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /**
     * Redraws the reused bitmap according to [mode]:
     *   - BOTH: upload top, download bottom (two lines)
     *   - DOWNLOAD / UPLOAD: single value centered vertically
     */
    fun render(sample: SpeedSample, mode: DisplayMode): Bitmap {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        when (mode) {
            DisplayMode.BOTH -> {
                drawLine("↑" + formatter.formatCompact(sample.uploadBps), UP_BASELINE_PX, TEXT_SIZE_PX)
                drawLine("↓" + formatter.formatCompact(sample.downloadBps), DOWN_BASELINE_PX, TEXT_SIZE_PX)
            }
            DisplayMode.DOWNLOAD -> {
                drawLine(
                    "↓" + formatter.formatCompact(sample.downloadBps),
                    CENTER_BASELINE_PX,
                    SINGLE_TEXT_SIZE_PX,
                    SINGLE_MAX_TEXT_WIDTH_PX,
                )
            }
            DisplayMode.UPLOAD -> {
                drawLine(
                    "↑" + formatter.formatCompact(sample.uploadBps),
                    CENTER_BASELINE_PX,
                    SINGLE_TEXT_SIZE_PX,
                    SINGLE_MAX_TEXT_WIDTH_PX,
                )
            }
        }
        return bitmap
    }

    private fun drawLine(
        text: String,
        baseline: Float,
        initialTextSizePx: Float,
        maxTextWidthPx: Float = MAX_TEXT_WIDTH_PX,
    ) {
        paint.textSize = initialTextSizePx
        val width = paint.measureText(text)
        if (width > maxTextWidthPx) {
            paint.textSize = initialTextSizePx * maxTextWidthPx / width
        }
        canvas.drawText(text, SIZE_PX / 2f, baseline, paint)
    }

    private companion object {
        const val SIZE_PX = 96
        const val TEXT_SIZE_PX = 48f
        const val SINGLE_TEXT_SIZE_PX = 84f
        const val UP_BASELINE_PX = 44f
        const val DOWN_BASELINE_PX = 94f
        // Center of 96px bitmap: cap-height ≈ 24px at textSize 48f → (96−24)/2+24 = 60f.
        const val CENTER_BASELINE_PX = 69f
        // Leave 4px breathing room on each side before the system clips.
        const val MAX_TEXT_WIDTH_PX = 88f
        // In single-line mode use almost full width, so large readings stay visually bigger.
        const val SINGLE_MAX_TEXT_WIDTH_PX = 96f
    }
}
