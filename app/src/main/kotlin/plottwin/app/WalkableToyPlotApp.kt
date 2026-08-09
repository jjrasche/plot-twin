package plottwin.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.factoredui.compose.scene3d.Scene3dView
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.prepare
import ai.factoredui.compose.scene3d.toPose
import java.nio.file.Path
import plottwin.eyes.realParcelSceneFromFile
import plottwin.eyes.toyPlotScene

// no argument: the toy plot; one argument: a compiled real-parcel file from capture/scripts/
fun main(args: Array<String>) {
    val walkable = if (args.isEmpty()) "toy plot" to toyPlotScene() else "real parcel" to realParcelSceneFromFile(Path.of(args[0]))
    val (plotName, scene) = walkable
    val spec = scene.spec
    val preparedMeshes = spec.meshesByEntity.mapValues { (_, mesh) -> mesh.prepare() }
    val overview = requireNotNull(spec.world.camera) { "the walkable scene must carry an overview camera" }
    application {
        Window(onCloseRequest = ::exitApplication, title = "plot-twin — walkable $plotName") {
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
