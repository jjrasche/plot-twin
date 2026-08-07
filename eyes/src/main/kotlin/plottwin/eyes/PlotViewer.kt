package plottwin.eyes

import ai.factoredui.compose.scene3d.Scene3dCameraPose
import ai.factoredui.compose.scene3d.captureScene3dPoseView
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.prepare
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import plottwin.render.SCENE_BACKGROUND
import plottwin.render.WalkableSceneSpec

const val VIEW_WIDTH = 640
const val VIEW_HEIGHT = 400

val BACKGROUND_ARGB: Int = (0xFF000000.toInt()) or SCENE_BACKGROUND.toInt(16)

class PlotViewer(
    private val spec: WalkableSceneSpec,
    val width: Int = VIEW_WIDTH,
    val height: Int = VIEW_HEIGHT,
) {
    private val preparedMeshes = spec.meshesByEntity.mapValues { (_, mesh) -> mesh.prepare() }

    fun capture(pose: Scene3dCameraPose): BufferedImage = decode(
        captureScene3dPoseView(
            world = spec.world,
            pose = pose,
            meshes = preparedMeshes,
            width = width,
            height = height,
        ),
    )

    fun captureWithout(entityName: String, pose: Scene3dCameraPose): BufferedImage {
        val worldWithoutEntity = spec.world.copy(entities = spec.world.entities.filterNot { it.id == entityName })
        return decode(
            captureScene3dPoseView(
                world = worldWithoutEntity,
                pose = pose,
                meshes = preparedMeshes - entityName,
                width = width,
                height = height,
            ),
        )
    }

    fun projectorFor(pose: Scene3dCameraPose): ScreenProjector = ScreenProjector(cameraOfPose(pose), width, height)

    private fun decode(png: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(png))
}
