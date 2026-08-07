package plottwin.app

import ai.factoredui.compose.scene3d.captureScene3dPoseView
import ai.factoredui.compose.scene3d.prepare
import ai.factoredui.compose.scene3d.toPose
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.eyes.toyPlotScene

private const val BACKGROUND_ARGB = 0xFF1E222A.toInt()

class WalkableSceneReceiptTest {

    @Test
    fun toy_plot_spec_renders_headless_to_a_receipt_png() {
        val spec = toyPlotScene().spec
        val overview = requireNotNull(spec.world.camera)
        val pngBytes = captureScene3dPoseView(
            world = spec.world,
            pose = overview.toPose(),
            meshes = spec.meshesByEntity.mapValues { (_, mesh) -> mesh.prepare() },
            width = 1280,
            height = 800,
        )
        val receipt = File(System.getProperty("user.dir"), "build/walkable_toy_plot.png")
        receipt.parentFile.mkdirs()
        receipt.writeBytes(pngBytes)
        println("[walkable] wrote ${receipt.absolutePath}")

        val bitmap = javax.imageio.ImageIO.read(receipt)
        var drawnSamples = 0
        var markerSamples = 0
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val argb = bitmap.getRGB(x, y)
                if (argb != BACKGROUND_ARGB) drawnSamples++
                if (isMarkerRed(argb)) markerSamples++
            }
        }
        assertTrue(drawnSamples > 5000, "expected terrain+entities on screen, saw $drawnSamples drawn samples")
        assertTrue(markerSamples > 0, "expected violation-marker red pixels, saw none")
    }

    private fun isMarkerRed(argb: Int): Boolean {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return red > 150 && red > green + 60 && red > blue + 60
    }
}
