package plottwin.app

import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.scene3d.Scene3dCameraState
import ai.factoredui.compose.scene3d.toVec3
import kotlin.math.asin
import kotlin.math.atan2

fun orbitCameraOf(state: Scene3dCameraState?): Camera {
    val camera = Camera()
    if (state == null) return camera
    val target = state.target.toVec3()
    val offset = state.position.toVec3() - target
    val distance = offset.length()
    camera.target = target
    if (distance > 1e-4f) {
        camera.distance = distance
        val direction = offset.normalize()
        camera.pitchRadians = asin(direction.y.coerceIn(-1f, 1f))
        camera.yawRadians = atan2(direction.x, direction.z)
    }
    state.fov?.let { camera.fovYRadians = it }
    return camera
}
