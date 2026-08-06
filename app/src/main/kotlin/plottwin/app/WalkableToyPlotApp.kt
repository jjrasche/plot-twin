package plottwin.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.factoredui.compose.scene3d.Scene3dView
import ai.factoredui.compose.scene3d.prepare
import plottwin.render.WalkableSceneSpec
import plottwin.render.projectWalkableScene
import plottwin.solvers.ToyPlotFixture
import plottwin.solvers.runSolvers

fun toyPlotSceneSpec(): WalkableSceneSpec {
    val world = ToyPlotFixture.toyWorld()
    return projectWalkableScene(world.state, world.terrain, runSolvers(world, ToyPlotFixture.toyConstraints()))
}

fun main() {
    val spec = toyPlotSceneSpec()
    val preparedMeshes = spec.meshesByEntity.mapValues { (_, mesh) -> mesh.prepare() }
    application {
        Window(onCloseRequest = ::exitApplication, title = "plot-twin — walkable toy plot") {
            val camera = remember { orbitCameraOf(spec.world.camera) }
            Scene3dView(
                world = spec.world,
                camera = camera,
                meshes = preparedMeshes,
                showGrid = false,
            )
        }
    }
}
