package plottwin.eyes

import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

const val LOBE_HALF_ANGLE_RADIANS = 0.6
const val LOBE_DARKENING = 0.4

fun paintShadowLobe(
    source: BufferedImage,
    centerX: Double,
    centerY: Double,
    screenRadians: Double,
    reach: Double,
): BufferedImage {
    val painted = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
    painted.graphics.drawImage(source, 0, 0, null)
    for (row in 0 until painted.height) {
        for (column in 0 until painted.width) {
            val offsetX = column - centerX
            val offsetY = row - centerY
            val distance = hypot(offsetX, offsetY)
            if (distance < SHADOW_INNER_RADIUS_PX || distance > reach) continue
            if (angleGap(atan2(offsetY, offsetX), screenRadians) > LOBE_HALF_ANGLE_RADIANS) continue
            val lit = painted.getRGB(column, row)
            if (lit == BACKGROUND_ARGB) continue
            painted.setRGB(column, row, darkened(lit))
        }
    }
    return painted
}

private fun angleGap(left: Double, right: Double): Double {
    val difference = abs(left - right) % (2 * PI)
    return if (difference > PI) 2 * PI - difference else difference
}

private fun darkened(argb: Int): Int {
    val red = (((argb shr 16) and 0xFF) * LOBE_DARKENING).toInt()
    val green = (((argb shr 8) and 0xFF) * LOBE_DARKENING).toInt()
    val blue = ((argb and 0xFF) * LOBE_DARKENING).toInt()
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
