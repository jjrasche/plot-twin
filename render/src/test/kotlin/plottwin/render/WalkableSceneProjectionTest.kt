package plottwin.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import plottwin.solvers.ToyPlotFixture
import plottwin.solvers.runSolvers
import plottwin.worldstate.CurrentState

class WalkableSceneProjectionTest {

    private val toySpec: WalkableSceneSpec by lazy {
        val world = ToyPlotFixture.toyWorld()
        projectWalkableScene(world.state, runSolvers(world, ToyPlotFixture.toyConstraints()))
    }

    @Test
    fun toy_spec_carries_terrain_three_entities_and_two_violation_markers() {
        assertEquals(
            setOf(
                TERRAIN_ENTITY_ID,
                "greenhouse",
                "pergola",
                "garden path",
                "violation-pergola-drainage-0",
                "violation-path-clearance-1",
            ),
            toySpec.meshesByEntity.keys,
        )
        assertEquals(toySpec.meshesByEntity.keys, toySpec.world.entities.map { it.id }.toSet())
        assertTrue(toySpec.world.camera != null, "spec must carry an overview camera")
    }

    @Test
    fun terrain_mesh_is_a_downsampled_heightfield_grid() {
        val terrainMesh = toySpec.meshesByEntity.getValue(TERRAIN_ENTITY_ID)

        assertEquals(225, terrainMesh.gridCellsX)
        assertEquals(225, terrainMesh.gridCellsZ)
        assertEquals(3 * 226 * 226, terrainMesh.vertices.size)
        assertEquals(2 * 225 * 225, terrainMesh.triColors.size)
    }

    @Test
    fun greenhouse_mesh_is_a_prism_footprint_extruded_from_ground_height() {
        val greenhouseMesh = toySpec.meshesByEntity.getValue("greenhouse")

        assertEquals(8 * 3, greenhouseMesh.vertices.size)
        assertEquals(12 * 3, greenhouseMesh.triangles.size)
        assertEquals(12, greenhouseMesh.triColors.size)
        val floorY = greenhouseMesh.vertices[1]
        val roofY = greenhouseMesh.vertices[4 * 3 + 1]
        assertEquals(7.3f, floorY, 1e-4f)
        assertEquals(floorY + 2.7432f, roofY, 1e-4f)
    }

    @Test
    fun garden_path_mesh_is_a_flat_ribbon_along_its_polyline() {
        val pathMesh = toySpec.meshesByEntity.getValue("garden path")

        assertEquals(4 * 3, pathMesh.vertices.size)
        assertEquals(2 * 3, pathMesh.triangles.size)
        val zValues = pathMesh.vertices.filterIndexed { index, _ -> index % 3 == 2 }
        assertTrue(zValues.all { it in 29.6f..30.4f }, "ribbon must run along north=75m (scene z=30), got $zValues")
    }

    @Test
    fun path_pinch_marker_stands_at_the_reported_pinch_point() {
        val pinchMarker = toySpec.meshesByEntity.getValue("violation-path-clearance-1")

        assertEquals(4.25f, pinchMarker.vertices[0], 1e-4f)
        assertEquals(30f, pinchMarker.vertices[2], 1e-4f)
        assertEquals(8, pinchMarker.triColors.size)
        assertTrue(pinchMarker.triColors.all { it == VIOLATION_MARKER_COLOR })
    }

    @Test
    fun marker_radius_grows_with_magnitude_within_bounds() {
        assertEquals(0.4f, markerRadiusOf(0.0))
        assertTrue(markerRadiusOf(2.0) > markerRadiusOf(0.5))
        assertEquals(2.0f, markerRadiusOf(1000.0))
    }

    @Test
    fun spec_round_trips_through_json() {
        assertEquals(toySpec, walkableSceneSpecFromJson(toySpec.toJson()))
    }

    @Test
    fun projection_before_any_base_terrain_row_fails_loudly() {
        assertFailsWith<IllegalArgumentException> {
            projectWalkableScene(CurrentState.EMPTY, emptyList())
        }
    }
}
