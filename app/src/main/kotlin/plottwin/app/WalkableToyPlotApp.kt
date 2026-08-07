package plottwin.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.factoredui.compose.scene3d.Scene3dView
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.prepare
import ai.factoredui.compose.scene3d.toPose
import plottwin.eyes.toyPlotScene

fun main() {
    val spec = toyPlotScene().spec
    val preparedMeshes = spec.meshesByEntity.mapValues { (_, mesh) -> mesh.prepare() }
    val overview = requireNotNull(spec.world.camera) { "the walkable scene must carry an overview camera" }
    application {
        Window(onCloseRequest = ::exitApplication, title = "plot-twin — walkable toy plot") {
            val camera = remember { cameraOfPose(overview.toPose()) }
            Scene3dView(
                world = spec.world,
                camera = camera,
                meshes = preparedMeshes,
                showGrid = false,
            )
        }
    }
}
