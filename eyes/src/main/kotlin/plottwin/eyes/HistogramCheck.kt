package plottwin.eyes

import java.awt.image.BufferedImage

const val HISTOGRAM_BUCKETS = 32
const val OCCUPIED_BUCKET_BOUND = 4
const val DOMINANT_BUCKET_BOUND = 0.92
const val CLIPPED_FRACTION_BOUND = 0.02
const val CLIPPED_HIGH_LUMINANCE = 250.0
const val CLIPPED_LOW_LUMINANCE = 4.0

data class LuminanceHistogram(
    val bucketCounts: IntArray,
    val pixelCount: Int,
    val clippedHigh: Int,
    val clippedLow: Int,
) {
    val occupiedBuckets: Int get() = bucketCounts.count { it > 0 }
    val dominantFraction: Double get() = (bucketCounts.max().toDouble()) / pixelCount
    val clippedHighFraction: Double get() = clippedHigh.toDouble() / pixelCount
    val clippedLowFraction: Double get() = clippedLow.toDouble() / pixelCount
}

fun luminanceHistogramOf(image: BufferedImage): LuminanceHistogram {
    val bucketCounts = IntArray(HISTOGRAM_BUCKETS)
    var clippedHigh = 0
    var clippedLow = 0
    for (row in 0 until image.height) {
        for (column in 0 until image.width) {
            val luminance = luminanceAt(image, column, row)
            bucketCounts[bucketOf(luminance)]++
            if (luminance >= CLIPPED_HIGH_LUMINANCE) clippedHigh++
            if (luminance <= CLIPPED_LOW_LUMINANCE) clippedLow++
        }
    }
    return LuminanceHistogram(bucketCounts, image.width * image.height, clippedHigh, clippedLow)
}

private fun bucketOf(luminance: Double): Int =
    ((luminance / 256.0) * HISTOGRAM_BUCKETS).toInt().coerceIn(0, HISTOGRAM_BUCKETS - 1)

fun histogramFindings(subject: String, histogram: LuminanceHistogram): List<EyeFinding> = listOf(
    EyeFinding(
        check = "histogram-not-blank",
        subject = subject,
        measured = histogram.occupiedBuckets.toDouble(),
        bound = OCCUPIED_BUCKET_BOUND.toDouble(),
        passed = histogram.occupiedBuckets >= OCCUPIED_BUCKET_BOUND && histogram.dominantFraction <= DOMINANT_BUCKET_BOUND,
        detail = "dominant bucket holds %.3f of the frame, bound $DOMINANT_BUCKET_BOUND".format(histogram.dominantFraction),
    ),
    EyeFinding(
        check = "histogram-not-blown-out",
        subject = subject,
        measured = maxOf(histogram.clippedHighFraction, histogram.clippedLowFraction),
        bound = CLIPPED_FRACTION_BOUND,
        passed = histogram.clippedHighFraction <= CLIPPED_FRACTION_BOUND &&
            histogram.clippedLowFraction <= CLIPPED_FRACTION_BOUND,
        detail = "clipped high %.4f, clipped low %.4f".format(histogram.clippedHighFraction, histogram.clippedLowFraction),
    ),
)
