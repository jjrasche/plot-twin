package plottwin.eyes

import ai.factoredui.compose.scene3d.Scene3dMesh
import java.awt.image.BufferedImage

const val SILHOUETTE_IOU_BOUND = 0.6
const val SILHOUETTE_MINIMUM_PIXELS = 200

fun renderedEntityMaskOf(withEntity: BufferedImage, withoutEntity: BufferedImage): BooleanArray {
    require(withEntity.width == withoutEntity.width && withEntity.height == withoutEntity.height) {
        "entity-mask renders differ in size"
    }
    val mask = BooleanArray(withEntity.width * withEntity.height)
    for (row in 0 until withEntity.height) {
        for (column in 0 until withEntity.width) {
            mask[row * withEntity.width + column] =
                withEntity.getRGB(column, row) != withoutEntity.getRGB(column, row)
        }
    }
    return mask
}

fun predictedEntityMaskOf(
    meshesByEntity: Map<String, Scene3dMesh>,
    entityName: String,
    projector: ScreenProjector,
): BooleanArray =
    rasterizeVisibleSurfaces(meshesByEntity, projector).maskOf(ownerIndexOf(meshesByEntity, entityName))

fun intersectionOverUnion(left: BooleanArray, right: BooleanArray): Double {
    require(left.size == right.size) { "mask sizes differ: ${left.size} vs ${right.size}" }
    var intersection = 0
    var union = 0
    for (index in left.indices) {
        if (left[index] && right[index]) intersection++
        if (left[index] || right[index]) union++
    }
    return if (union == 0) 0.0 else intersection.toDouble() / union
}

fun countSet(mask: BooleanArray): Int = mask.count { it }

fun silhouetteFinding(
    viewer: PlotViewer,
    viewpoint: Viewpoint,
    entityName: String,
    meshesByEntity: Map<String, Scene3dMesh>,
    renderedView: BufferedImage,
): EyeFinding {
    val rendered = renderedEntityMaskOf(renderedView, viewer.captureWithout(entityName, viewpoint.pose))
    val predicted = predictedEntityMaskOf(meshesByEntity, entityName, viewer.projectorFor(viewpoint.pose))
    val renderedPixels = countSet(rendered)
    val predictedPixels = countSet(predicted)
    if (renderedPixels < SILHOUETTE_MINIMUM_PIXELS || predictedPixels < SILHOUETTE_MINIMUM_PIXELS) {
        return EyeFinding(
            check = "silhouette-iou",
            subject = "${viewpoint.name}/$entityName",
            measured = 0.0,
            bound = SILHOUETTE_IOU_BOUND,
            passed = false,
            detail = "too small to judge: rendered $renderedPixels px, predicted $predictedPixels px",
        )
    }
    val iou = intersectionOverUnion(rendered, predicted)
    return EyeFinding(
        check = "silhouette-iou",
        subject = "${viewpoint.name}/$entityName",
        measured = iou,
        bound = SILHOUETTE_IOU_BOUND,
        passed = iou >= SILHOUETTE_IOU_BOUND,
        detail = "rendered $renderedPixels px vs footprint-projection $predictedPixels px",
    )
}
