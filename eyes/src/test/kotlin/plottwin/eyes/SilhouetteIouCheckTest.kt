package plottwin.eyes

import kotlin.test.Test
import kotlin.test.assertTrue

class SilhouetteIouCheckTest {

    private fun greenhouseViewpoint(scene: PlotScene): Viewpoint =
        plotViewpoints(scene.state).first { it.subject == "greenhouse" }

    @Test
    fun a_drawn_entity_covers_the_pixels_its_footprint_projects_onto() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = greenhouseViewpoint(scene)
        val finding = silhouetteFinding(
            viewer = viewer,
            viewpoint = viewpoint,
            entityName = "greenhouse",
            meshesByEntity = terrainAndEntityMeshesOf(scene.spec),
            renderedView = viewer.capture(viewpoint.pose),
        )
        assertTrue(finding.passed, finding.line())
    }

    @Test
    fun a_footprint_projected_through_the_wrong_camera_is_rejected() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = greenhouseViewpoint(scene)
        val rendered = renderedEntityMaskOf(
            viewer.capture(viewpoint.pose),
            viewer.captureWithout("greenhouse", viewpoint.pose),
        )
        val fromElsewhere = predictedEntityMaskOf(
            terrainAndEntityMeshesOf(scene.spec),
            "greenhouse",
            viewer.projectorFor(plotViewpoints(scene.state).first { it.name == "overhead" }.pose),
        )
        val iou = intersectionOverUnion(rendered, fromElsewhere)
        assertTrue(iou < SILHOUETTE_IOU_BOUND, "an overhead projection should not match a walk-height render, got $iou")
    }

    @Test
    fun identical_masks_score_one_and_disjoint_masks_score_zero() {
        val left = booleanArrayOf(true, true, false, false)
        val right = booleanArrayOf(false, false, true, true)
        assertTrue(intersectionOverUnion(left, left) == 1.0)
        assertTrue(intersectionOverUnion(left, right) == 0.0)
    }
}
