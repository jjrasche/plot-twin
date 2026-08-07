package plottwin.solvers

import kotlin.math.abs
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.Meters

private const val SIDE_CELLS = 40
private const val CELL_METERS = 0.5

private fun flatSurface(): OccluderSurface =
    OccluderSurface(SIDE_CELLS, SIDE_CELLS, Meters(CELL_METERS), FloatArray(SIDE_CELLS * SIDE_CELLS))

private fun cellAt(column: Int, row: Int): Int = row * SIDE_CELLS + column

private fun surfaceWithWallOnRow(wallRow: Int, wallHeight: Float): OccluderSurface {
    val surface = flatSurface()
    for (column in 0 until SIDE_CELLS) surface.heights[cellAt(column, wallRow)] = wallHeight
    return surface
}

class HorizonSweepTest {

    @Test
    fun flat_ground_has_no_horizon_in_any_direction() {
        val surface = flatSurface()
        for (azimuth in 0 until 360 step 15) {
            val slopes = horizonSlopesToward(surface, azimuth.toDouble())
            assertTrue(slopes.all { it == 0f }, "flat ground raised a horizon at azimuth $azimuth")
        }
    }

    @Test
    fun a_wall_to_the_south_raises_the_horizon_of_the_cell_behind_it_and_not_the_one_in_front() {
        val wallHeight = 3.0f
        val surface = surfaceWithWallOnRow(wallRow = 20, wallHeight = wallHeight)
        val slopes = horizonSlopesToward(surface, sunAzimuthDegrees = 180.0)
        val twoCellsNorth = slopes[cellAt(10, 22)]
        val twoCellsSouth = slopes[cellAt(10, 18)]
        assertEquals(wallHeight / (2 * CELL_METERS).toFloat(), twoCellsNorth, 1e-3f)
        assertEquals(0f, twoCellsSouth)
    }

    @Test
    fun the_horizon_a_wall_raises_falls_off_with_distance() {
        val surface = surfaceWithWallOnRow(wallRow = 20, wallHeight = 3.0f)
        val slopes = horizonSlopesToward(surface, sunAzimuthDegrees = 180.0)
        val near = slopes[cellAt(10, 22)]
        val far = slopes[cellAt(10, 30)]
        assertTrue(near > far, "horizon at 1m ($near) should out-rise the one at 5m ($far)")
        assertTrue(far > 0f, "the wall should still be on the far cell's horizon")
    }

    @Test
    fun a_sun_below_the_wall_top_leaves_the_cell_behind_it_unlit_and_above_it_fully_lit() {
        val surface = surfaceWithWallOnRow(wallRow = 20, wallHeight = 3.0f)
        val shadedCell = cellAt(10, 22)
        val wallTopDegrees = Math.toDegrees(atan(3.0 / 1.0))
        val underWall = sunlitFractionsAt(surface, SunRay(180.0, wallTopDegrees - 5.0))[shadedCell]
        val overWall = sunlitFractionsAt(surface, SunRay(180.0, wallTopDegrees + 5.0))[shadedCell]
        assertEquals(0f, underWall)
        assertEquals(1f, overWall)
    }

    @Test
    fun the_terminator_is_a_ramp_the_width_of_the_suns_disc_not_a_step() {
        val surface = surfaceWithWallOnRow(wallRow = 20, wallHeight = 3.0f)
        val shadedCell = cellAt(10, 22)
        val wallTopDegrees = Math.toDegrees(atan(3.0 / 1.0))
        val halfLit = sunlitFractionsAt(surface, SunRay(180.0, wallTopDegrees))[shadedCell]
        assertTrue(abs(halfLit - 0.5f) < 0.05f, "the sun's centre on the horizon should read about half lit, got $halfLit")
    }

    @Test
    fun a_pit_sees_less_sky_than_the_flat_ground_around_it() {
        val surface = flatSurface()
        for (row in 18..22) {
            for (column in 18..22) surface.heights[cellAt(column, row)] = -1.5f
        }
        val openness = skyOpennessOf(surface)
        assertTrue(openness[cellAt(20, 20)] < openness[cellAt(5, 5)], "the pit floor should be more occluded than open flat")
        assertTrue(openness[cellAt(5, 5)] > 0.99f, "open flat ground should see essentially the whole sky")
    }

    // A cell the sweep skipped keeps the array's zero, so a plane that owes every cell the same
    // non-zero horizon is what makes missed cells visible at all.
    @Test
    fun a_plane_tilted_into_the_sun_reads_its_own_gradient_back_at_every_cell() {
        val gradient = 0.2
        for (azimuth in 0 until 360 step 15) {
            val slopes = horizonSlopesToward(planeTiltedToward(azimuth.toDouble(), gradient), azimuth.toDouble())
            val agreeing = slopes.count { abs(it - gradient) < 0.02 }
            val lineStarts = 3 * SIDE_CELLS
            assertTrue(
                agreeing >= slopes.size - lineStarts,
                "azimuth $azimuth: only $agreeing of ${slopes.size} cells saw the plane's own $gradient horizon",
            )
        }
    }
}

private fun planeTiltedToward(azimuthDegrees: Double, gradient: Double): OccluderSurface {
    val towardSun = Math.toRadians(azimuthDegrees)
    val surface = flatSurface()
    for (row in 0 until SIDE_CELLS) {
        for (column in 0 until SIDE_CELLS) {
            val east = (column + 0.5) * CELL_METERS
            val north = (row + 0.5) * CELL_METERS
            surface.heights[cellAt(column, row)] =
                (gradient * (east * Math.sin(towardSun) + north * Math.cos(towardSun))).toFloat()
        }
    }
    return surface
}
