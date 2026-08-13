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

// The shade attribution depends on the state and the sun, not the pose, so it is read once
// and looked at from every viewpoint.
fun inspectPlot(scene: PlotScene, viewer: PlotViewer): List<ViewpointInspection> {
    val shadowedGround = shadowedGroundOf(scene.state, scene.daylight.sun)
    return plotViewpoints(scene.state).map { viewpoint -> inspectViewpoint(scene, viewer, viewpoint, shadowedGround) }
}

fun inspectViewpoint(
    scene: PlotScene,
    viewer: PlotViewer,
    viewpoint: Viewpoint,
    shadowedGround: List<ShadowedGroundPoint> = shadowedGroundOf(scene.state, scene.daylight.sun),
): ViewpointInspection {
    val image = viewer.capture(viewpoint.pose)
    val projector = viewer.projectorFor(viewpoint.pose)
    val classifier = skyClassifierOf(scene.spec, scene.daylight)
    val solidMeshes = terrainAndEntityMeshesOf(scene.spec)
    val solid = rasterizeVisibleSurfaces(solidMeshes, projector)
    val predictedSkyline = predictedSkylineOf(solid)
    val observedSkyline = if (classifier == null) observedSkylineOf(image) else observedSkylineOf(image, classifier)
    val findings = ArrayList<EyeFinding>()
    findings += skylineFindings(viewpoint.name, compareSkylines(observedSkyline, predictedSkyline))
    if (classifier != null) {
        findings += skyRegionFindings(viewpoint.name, skyRegionReadingOf(image, classifier, solid))
    }
    findings += histogramFindings(viewpoint.name, luminanceHistogramOf(image))
    findings += shadowFindingAt(scene, viewer, viewpoint, image, classifier, shadowedGround)
    val subject = viewpoint.subject
    if (subject != null && solidMeshes.containsKey(subject)) {
        findings += silhouetteFinding(viewer, viewpoint, subject, solidMeshes, image)
    }
    return ViewpointInspection(viewpoint, image, findings)
}

// One claim, read from every angle: the plot's principal shadow points away from the sun.
// The tallest thing on the plot owns that shadow â€” a low viewpoint's own subject may be a
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

data class ShadowReading(
    val estimate: ShadowEstimate,
    val expectedScreenRadians: Double,
    val population: CasterPopulation,
)

fun shadowFindingAt(
    scene: PlotScene,
    viewer: PlotViewer,
    viewpoint: Viewpoint,
    image: BufferedImage,
    classifier: SkyClassifier?,
    shadowedGround: List<ShadowedGroundPoint> = shadowedGroundOf(scene.state, scene.daylight.sun),
): EyeFinding {
    val reading = shadowReadingAt(scene, viewer, viewpoint, image, classifier, shadowedGround)
        ?: return EyeFinding(
            check = "shadow-direction",
            subject = viewpoint.name,
            measured = 0.0,
            bound = SHADOW_CONTRAST_FLOOR,
            passed = false,
            detail = "the sampled ground point does not project into this view",
            advisory = true,
        )
    return shadowFinding(viewpoint.name, reading.estimate, reading.expectedScreenRadians, reading.population)
}

// One assembly of the reading — the darkest bearing, the sun's bearing, and the caster
// population that says whether comparing them means anything — so no caller can pair a
// bearing with a population measured over a different ring.
fun shadowReadingAt(
    scene: PlotScene,
    viewer: PlotViewer,
    viewpoint: Viewpoint,
    image: BufferedImage,
    classifier: SkyClassifier?,
    shadowedGround: List<ShadowedGroundPoint> = shadowedGroundOf(scene.state, scene.daylight.sun),
): ShadowReading? {
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
    if (!anchor.visible || shadow == null) return null
    val (innerRadius, outerRadius) = shadowAnnulusOf(shadow.lengthPx)
    val casterMask = casterMaskOf(scene, viewer, caster, viewpoint, image)
    return ShadowReading(
        estimate = estimateShadowDirection(
            image,
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            innerRadius,
            outerRadius,
            casterMask,
            skyPixelOf(classifier),
        ),
        expectedScreenRadians = shadow.screenRadians,
        population = casterPopulationInView(
            shadowedGround,
            caster,
            projector,
            image,
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            innerRadius,
            outerRadius,
            casterMask,
            skyPixelOf(classifier),
        ),
    )
}

fun skyPixelOf(classifier: SkyClassifier?): ((Int) -> Boolean)? =
    classifier?.let { it::isSky }

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
