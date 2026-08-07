package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import java.awt.image.BufferedImage
import plottwin.render.groundHeightAt
import plottwin.render.sceneFrameOf
import plottwin.worldstate.CurrentState

const val SHADOW_INNER_RADIUS_PX = 14.0
const val SHADOW_OUTER_RADIUS_PX = 60.0

data class ViewpointInspection(
    val viewpoint: Viewpoint,
    val image: BufferedImage,
    val findings: List<EyeFinding>,
)

fun inspectPlot(scene: PlotScene, viewer: PlotViewer): List<ViewpointInspection> =
    plotViewpoints(scene.state).map { viewpoint -> inspectViewpoint(scene, viewer, viewpoint) }

fun inspectViewpoint(scene: PlotScene, viewer: PlotViewer, viewpoint: Viewpoint): ViewpointInspection {
    val image = viewer.capture(viewpoint.pose)
    val projector = viewer.projectorFor(viewpoint.pose)
    val findings = ArrayList<EyeFinding>()
    findings += skylineFindings(
        viewpoint.name,
        compareSkylines(observedSkylineOf(image), predictedSkylineOf(scene.spec.meshesByEntity.values, projector)),
    )
    findings += histogramFindings(viewpoint.name, luminanceHistogramOf(image))
    findings += shadowFindingAt(scene, viewer, viewpoint, image)
    val subject = viewpoint.subject
    if (subject != null && scene.spec.meshesByEntity.containsKey(subject)) {
        findings += silhouetteFinding(viewer, viewpoint, subject, scene.spec.meshesByEntity, image)
    }
    return ViewpointInspection(viewpoint, image, findings)
}

// One claim, read from every angle: the plot's principal shadow points away from the sun.
// The tallest thing on the plot owns that shadow — a low viewpoint's own subject may be a
// stake in a ditch whose trench is darker than anything it casts.
fun principalShadowCasterOf(state: CurrentState): String? =
    state.entities.entries
        .filter { (_, placed) -> placed.footprint.size >= 3 && placed.height.value > 0.0 }
        .maxByOrNull { (_, placed) -> placed.height.value }
        ?.key

fun groundSampleOf(state: CurrentState, viewpoint: Viewpoint): Vec3 {
    val terrain = requireNotNull(state.terrain) { "shadow sampling needs a base-terrain row" }.grid
    val frame = sceneFrameOf(terrain)
    val placed = principalShadowCasterOf(state)?.let { state.entities[it] }
    if (placed == null) {
        return Vec3(
            frame.sceneX(terrain.columns * terrain.cellSize.value / 2.0),
            terrain.surfaceHeights.average().toFloat(),
            frame.sceneZ(terrain.rows * terrain.cellSize.value / 2.0),
        )
    }
    val centroid = footprintCentroid(placed.footprint)
    return Vec3(
        frame.sceneX(centroid.east.value),
        groundHeightAt(terrain, centroid),
        frame.sceneZ(centroid.north.value),
    )
}

private fun shadowFindingAt(
    scene: PlotScene,
    viewer: PlotViewer,
    viewpoint: Viewpoint,
    image: BufferedImage,
): EyeFinding {
    val projector = viewer.projectorFor(viewpoint.pose)
    val caster = principalShadowCasterOf(scene.state)
    val casterHeight = caster?.let { scene.state.entities.getValue(it).height.value } ?: 0.0
    val groundPoint = groundSampleOf(scene.state, viewpoint)
    val anchor = projector.project(groundPoint)
    val shadow = projectShadow(
        projector,
        groundPoint,
        scene.daylight.sun.azimuthDegrees,
        castShadowMetersOf(casterHeight, scene.daylight.sun.altitudeDegrees),
    )
    if (!anchor.visible || shadow == null) {
        return EyeFinding(
            check = "shadow-direction",
            subject = viewpoint.name,
            measured = 0.0,
            bound = SHADOW_CONTRAST_FLOOR,
            passed = false,
            detail = "the sampled ground point does not project into this view",
            advisory = true,
        )
    }
    val (innerRadius, outerRadius) = shadowAnnulusOf(shadow.lengthPx)
    return shadowFinding(
        subject = viewpoint.name,
        advisory = false,
        estimate = estimateShadowDirection(
            image,
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            innerRadius,
            outerRadius,
            casterMaskOf(scene, viewer, caster, viewpoint, image),
        ),
        expectedScreenRadians = shadow.screenRadians,
    )
}

private fun casterMaskOf(
    scene: PlotScene,
    viewer: PlotViewer,
    caster: String?,
    viewpoint: Viewpoint,
    image: BufferedImage,
): BooleanArray? {
    if (caster == null || !scene.spec.meshesByEntity.containsKey(caster)) return null
    return renderedEntityMaskOf(image, viewer.captureWithout(caster, viewpoint.pose))
}
