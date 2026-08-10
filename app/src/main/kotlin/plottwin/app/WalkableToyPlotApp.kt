package plottwin.app

import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.factoredui.compose.scene3d.Scene3dView
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.prepare
import ai.factoredui.compose.scene3d.toPose
import java.nio.file.Path
import plottwin.eyes.StageDiffScene
import plottwin.eyes.bermSpoilScene
import plottwin.eyes.digHereScene
import plottwin.eyes.foundationPadScene
import plottwin.eyes.realParcelSceneFromFile
import plottwin.eyes.toyPlotScene
import plottwin.render.paintStageDiff
import plottwin.render.projectStageDiff

// no argument: the walkable toy plot; one path argument: a compiled real-parcel file from
// capture/scripts/; "--stage-diff [dig|foundation|berm]": the glanceable diff for that demo stage
fun main(args: Array<String>) {
    if (args.firstOrNull() == "--stage-diff") {
        showStageDiff(stageDiffDemoSceneOf(args.getOrNull(1)))
        return
    }
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

private fun stageDiffDemoSceneOf(intentWord: String?): StageDiffScene = when (intentWord) {
    null, "berm" -> bermSpoilScene()
    "dig" -> digHereScene()
    "foundation" -> foundationPadScene()
    else -> error("unknown stage-diff demo intent '$intentWord' — use dig, foundation, or berm")
}

private fun showStageDiff(scene: StageDiffScene) {
    val diffImage = paintStageDiff(projectStageDiff(scene.state, scene.proposal)).image.toComposeImageBitmap()
    application {
        Window(onCloseRequest = ::exitApplication, title = "plot-twin — stage diff: ${scene.stageName}") {
            Image(bitmap = diffImage, contentDescription = scene.stageName)
        }
    }
}
