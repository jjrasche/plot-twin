package plottwin.eyes

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

class HistogramCheckTest {

    private fun flatImage(fill: Color): BufferedImage {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.color = fill
        canvas.fillRect(0, 0, image.width, image.height)
        canvas.dispose()
        return image
    }

    @Test
    fun a_blank_frame_fails_the_not_blank_check() {
        val findings = histogramFindings("blank", luminanceHistogramOf(flatImage(Color(0x1E, 0x22, 0x2A))))
        assertTrue(findings.first { it.check == "histogram-not-blank" }.passed.not(), "a single-tone frame must fail")
    }

    @Test
    fun a_blown_out_frame_fails_the_clipping_check() {
        val findings = histogramFindings("white", luminanceHistogramOf(flatImage(Color.WHITE)))
        assertTrue(findings.first { it.check == "histogram-not-blown-out" }.passed.not(), "an all-white frame must fail")
    }

    @Test
    fun a_rendered_toy_plot_view_passes_both_histogram_checks() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = plotViewpoints(scene.state).first { it.name == "overhead" }
        val findings = histogramFindings(viewpoint.name, luminanceHistogramOf(viewer.capture(viewpoint.pose)))
        assertTrue(failedFindings(findings).isEmpty(), findings.joinToString("\n") { it.line() })
    }
}
