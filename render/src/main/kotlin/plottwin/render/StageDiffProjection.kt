package plottwin.render

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import plottwin.worldstate.CONSERVATION_TOLERANCE_CUBIC_METERS
import plottwin.worldstate.CurrentState
import plottwin.worldstate.EarthworkTotals
import plottwin.worldstate.Surface
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.earthworkLedgerOf

const val CUBIC_YARDS_PER_CUBIC_METER = 1.3079506
const val FEET_PER_METER = 3.28084
// comparative anchor: ~10 yd^3 bank volume per dump-truck load (charter 17's figure, stated in the legend)
const val BANK_CUBIC_YARDS_PER_TRUCK_LOAD = 10.0

enum class DiffRegionKind { CUT, FILL }

class DiffRegion(
    val kind: DiffRegionKind,
    val cells: IntArray,
    val centroidColumn: Double,
    val centroidRow: Double,
    val peakDeltaMeters: Double,
)

data class MaterialMovement(val fromCutRegion: Int, val toFillRegion: Int, val looseCubicYards: Double)

data class HaulOffFlow(val fromCutRegion: Int, val looseCubicYards: Double)

data class StageDiffLegend(
    val dugLine: String,
    val placedLine: String,
    val hauledLine: String,
    val anchorLine: String,
)

class StageDiffSpec(
    val proposalName: String,
    val columns: Int,
    val rows: Int,
    val cellSizeMeters: Double,
    val measuredHeights: FloatArray,
    val deltaMeters: FloatArray,
    val maxAbsDeltaMeters: Double,
    val regions: List<DiffRegion>,
    val movements: List<MaterialMovement>,
    val haulOff: HaulOffFlow?,
    val ledger: EarthworkTotals,
    val legend: StageDiffLegend,
)

// Pure function of (state, measured surface, proposed surface). Volumes come only from the ledger rows.
fun projectStageDiff(state: CurrentState, proposal: Surface.Proposed): StageDiffSpec {
    val measured = requireNotNull(state.terrainOn(Surface.Measured)) { "a stage diff needs measured ground" }.grid
    val proposed = requireNotNull(state.terrainOn(proposal)) { "no proposed surface named ${proposal.name}" }.grid
    require(measured.columns == proposed.columns && measured.rows == proposed.rows) {
        "measured and proposed grids disagree on shape"
    }
    val delta = FloatArray(measured.cellCount) { proposed.surfaceHeights[it] - measured.surfaceHeights[it] }
    val regions = diffRegionsOf(measured, delta)
    val ledger = earthworkLedgerOf(state.earthworks.filter { it.row.surfaceName == proposal.name }).plot
    return StageDiffSpec(
        proposalName = proposal.name,
        columns = measured.columns,
        rows = measured.rows,
        cellSizeMeters = measured.cellSize.value,
        measuredHeights = measured.surfaceHeights,
        deltaMeters = delta,
        maxAbsDeltaMeters = delta.maxOfOrNull { abs(it.toDouble()) } ?: 0.0,
        regions = regions,
        movements = movementsOf(regions, ledger),
        haulOff = haulOffOf(regions, ledger),
        ledger = ledger,
        legend = stageDiffLegendOf(ledger),
    )
}

fun stageDiffLegendOf(ledger: EarthworkTotals): StageDiffLegend {
    val dugYards = ledger.bankCutCubicMeters * CUBIC_YARDS_PER_CUBIC_METER
    val placedYards = ledger.looseSpoilPlacedCubicMeters * CUBIC_YARDS_PER_CUBIC_METER
    val hauledYards = ledger.haulOffCubicMeters * CUBIC_YARDS_PER_CUBIC_METER
    val truckLoads = ceil(dugYards / BANK_CUBIC_YARDS_PER_TRUCK_LOAD).toInt()
    return StageDiffLegend(
        dugLine = "Dig out: ${ownerYards(dugYards)} cubic yards of soil",
        placedLine = "Place on site: ${ownerYards(placedYards)} cubic yards (dug soil fluffs up)",
        hauledLine = "Haul away: ${ownerYards(hauledYards)} cubic yards",
        anchorLine = "That digging is about $truckLoads dump-truck ${loadWord(truckLoads)} (a truck carries ~10 cubic yards)",
    )
}

fun ownerYards(cubicYards: Double): String = String.format(Locale.ROOT, "%.1f", cubicYards)

fun ownerFeet(meters: Double): String = String.format(Locale.ROOT, "%.1f", meters * FEET_PER_METER)

private fun loadWord(loads: Int): String = if (loads == 1) "load" else "loads"

private fun movementsOf(regions: List<DiffRegion>, ledger: EarthworkTotals): List<MaterialMovement> {
    if (ledger.looseSpoilPlacedCubicMeters <= CONSERVATION_TOLERANCE_CUBIC_METERS) return emptyList()
    val cut = largestRegionIndex(regions, DiffRegionKind.CUT) ?: return emptyList()
    val fill = largestRegionIndex(regions, DiffRegionKind.FILL) ?: return emptyList()
    return listOf(MaterialMovement(cut, fill, ledger.looseSpoilPlacedCubicMeters * CUBIC_YARDS_PER_CUBIC_METER))
}

private fun haulOffOf(regions: List<DiffRegion>, ledger: EarthworkTotals): HaulOffFlow? {
    if (ledger.haulOffCubicMeters <= CONSERVATION_TOLERANCE_CUBIC_METERS) return null
    val cut = largestRegionIndex(regions, DiffRegionKind.CUT) ?: return null
    return HaulOffFlow(cut, ledger.haulOffCubicMeters * CUBIC_YARDS_PER_CUBIC_METER)
}

private fun largestRegionIndex(regions: List<DiffRegion>, kind: DiffRegionKind): Int? =
    regions.withIndex().filter { it.value.kind == kind }.maxByOrNull { it.value.cells.size }?.index

private fun diffRegionsOf(grid: TerrainGrid, delta: FloatArray): List<DiffRegion> {
    val visited = BooleanArray(grid.cellCount)
    val regions = ArrayList<DiffRegion>()
    for (seed in 0 until grid.cellCount) {
        if (visited[seed] || delta[seed] == 0.0f) continue
        regions += floodRegionFrom(grid, delta, visited, seed)
    }
    return regions
}

private fun floodRegionFrom(grid: TerrainGrid, delta: FloatArray, visited: BooleanArray, seed: Int): DiffRegion {
    val kind = if (delta[seed] < 0.0f) DiffRegionKind.CUT else DiffRegionKind.FILL
    val cells = ArrayList<Int>()
    val frontier = ArrayDeque<Int>()
    visited[seed] = true
    frontier.addLast(seed)
    while (frontier.isNotEmpty()) {
        val cell = frontier.removeLast()
        cells += cell
        forEachSameSignNeighbor(grid, delta, cell, kind) { neighbor ->
            if (!visited[neighbor]) {
                visited[neighbor] = true
                frontier.addLast(neighbor)
            }
        }
    }
    return DiffRegion(
        kind = kind,
        cells = cells.toIntArray(),
        centroidColumn = cells.sumOf { grid.columnOf(it).toDouble() } / cells.size,
        centroidRow = cells.sumOf { grid.rowOf(it).toDouble() } / cells.size,
        peakDeltaMeters = cells.maxOf { abs(delta[it].toDouble()) },
    )
}

private inline fun forEachSameSignNeighbor(
    grid: TerrainGrid,
    delta: FloatArray,
    cell: Int,
    kind: DiffRegionKind,
    visit: (Int) -> Unit,
) {
    val column = grid.columnOf(cell)
    val row = grid.rowOf(cell)
    if (column > 0) visitIfSameSign(grid.indexOf(column - 1, row), delta, kind, visit)
    if (column < grid.columns - 1) visitIfSameSign(grid.indexOf(column + 1, row), delta, kind, visit)
    if (row > 0) visitIfSameSign(grid.indexOf(column, row - 1), delta, kind, visit)
    if (row < grid.rows - 1) visitIfSameSign(grid.indexOf(column, row + 1), delta, kind, visit)
}

private inline fun visitIfSameSign(neighbor: Int, delta: FloatArray, kind: DiffRegionKind, visit: (Int) -> Unit) {
    val sameSign = if (kind == DiffRegionKind.CUT) delta[neighbor] < 0.0f else delta[neighbor] > 0.0f
    if (sameSign) visit(neighbor)
}
