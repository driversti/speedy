package dev.seniorjava.speedy.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import dev.seniorjava.speedy.domain.SpeedFormatter
import dev.seniorjava.speedy.domain.SpeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Renders the monochromatic status-bar icon. Per spec (section 5):
 *   - Bitmap is ALPHA_8, 48x48 px, alpha-only (4x less memory than ARGB_8888).
 *   - A single Bitmap + Canvas is reused across ticks — we CLEAR it, redraw
 *     the new text, and hand the same instance to `Icon.createWithBitmap()`.
 *   - Paint.color = WHITE — the system uses only the alpha channel.
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
        textSize = TEXT_SIZE_PX
    }

    /**
     * Redraws the reused bitmap with `upload` (top) and `download` (bottom)
     * values. Thread-confined to the service coroutine: always called on the
     * same dispatcher, so no external synchronization is needed.
     */
    fun render(sample: SpeedSample): Bitmap {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val up = "↑ " + formatter.formatCompact(sample.uploadBps)
        val down = "↓ " + formatter.formatCompact(sample.downloadBps)

        canvas.drawText(up, SIZE_PX / 2f, UP_BASELINE_PX, paint)
        canvas.drawText(down, SIZE_PX / 2f, DOWN_BASELINE_PX, paint)
        return bitmap
    }

    private companion object {
        const val SIZE_PX = 48
        const val TEXT_SIZE_PX = 18f
        const val UP_BASELINE_PX = 19f
        const val DOWN_BASELINE_PX = 42f
    }
}
