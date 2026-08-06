package plottwin.solvers

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.GriddedElevationOperator
import plottwin.worldstate.ProjectedTerrain
import plottwin.worldstate.WorldLog

class TerrainFromLogGateTest {

    @Test
    fun projected_toy_grid_equals_the_directly_built_grid() {
        assertEquals(ToyPlotFixture.terrainWithSwale(), ToyPlotFixture.toyState().terrain!!.grid)
    }

    @Test
    fun solver_results_on_the_projected_grid_match_the_side_channel_grid() {
        val sideChannelState = ToyPlotFixture.toyState().copy(
            terrain = ProjectedTerrain(versionSeq = 0L, grid = ToyPlotFixture.terrainWithSwale()),
        )

        val fromLog = runSolvers(ToyPlotFixture.toyWorld(), ToyPlotFixture.toyConstraints())
        val fromSideChannel = runSolvers(SolverWorld(sideChannelState, ToyPlotFixture.toyDate), ToyPlotFixture.toyConstraints())

        assertEquals(fromSideChannel, fromLog)
    }

    @Test
    fun toy_base_terrain_row_stays_liftable() {
        val base = GriddedElevationOperator.compileBaseTerrain(ToyPlotFixture.rawSwaleElevation())
        val rawFloatBytes = ToyPlotFixture.CELLS_PER_SIDE * ToyPlotFixture.CELLS_PER_SIDE * Float.SIZE_BYTES

        val dbPath = createTempDirectory("terrain-size").resolve("world.db")
        WorldLog.open(dbPath).use { log -> ToyPlotFixture.appendToyPlot(log) }
        val toyLogBytes = Files.size(dbPath)

        println("toy base terrain: heightsBase64=${base.heightsBase64.length} bytes, raw float32=$rawFloatBytes bytes, whole toy log db=$toyLogBytes bytes")
        assertTrue(base.heightsBase64.length < 5_000_000)
    }
}
