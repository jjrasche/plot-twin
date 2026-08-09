package plottwin.oppipeline

import kotlin.math.max
import plottwin.geometry.isInsidePolygon
import plottwin.worldstate.CONSERVATION_TOLERANCE_CUBIC_METERS
import plottwin.worldstate.EarthworkRow
import plottwin.worldstate.FactorProvenance
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpSlot
import plottwin.worldstate.ProjectedTerrain
import plottwin.worldstate.RejectedViolation
import plottwin.worldstate.RejectionRow
import plottwin.worldstate.Surface
import plottwin.worldstate.TerrainDiffRow
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.encodeHeightsBase64
import plottwin.worldstate.isConserved

enum class GroundForm { POND, TERRACE, SWALE, PAD, BERM, GRADE }

// v1 factors assumed for clay soils, from Q-005's cited ranges (Crooks 2013: clay swell 30-50%, shrink 10-18%)
const val ASSUMED_SWELL_FACTOR = 0.30
const val ASSUMED_SHRINK_FACTOR = 0.15
const val POND_DEPTH_METERS = 1.0
const val HAUL_OFF_DESTINATION = "haul-off"

fun proposalSurfaceNameOf(opSeq: Long): String = "regrade-$opSeq"

fun resolveRegrade(world: PlacementWorld, opSeq: Long, op: OpRow): PlacementVerdict {
    val subjectName = op.slots[OpSlot.SUBJECT] ?: return slotRejection(op, "subject-missing")
    val form = groundFormOf(op.slots[OpSlot.GROUND_FORM]) ?: return slotRejection(op, "ground-form-unreadable")
    val subjectRing = regionRingOf(world, subjectName) ?: return slotRejection(op, "subject-region-missing")
    val measured = world.projection.terrain ?: return slotRejection(op, "measured-terrain-missing")
    val digRegion = regionCellsOf(measured.grid, subjectRing)
    if (digRegion.insideCellCount == 0) return slotRejection(op, "subject-region-off-terrain")
    val dig = digPatchFor(form, measured.grid, digRegion) ?: return slotRejection(op, "ground-form-unsupported")
    return resolveSpoil(world, opSeq, op, measured, subjectRing, dig)
}

private fun resolveSpoil(
    world: PlacementWorld,
    opSeq: Long,
    op: OpRow,
    measured: ProjectedTerrain,
    subjectRing: List<GroundPoint>,
    dig: DigPatch,
): PlacementVerdict {
    val looseAvailable = dig.bankCutCubicMeters * (1.0 + ASSUMED_SWELL_FACTOR) - looseEquivalentOfFill(dig.compactedFillCubicMeters)
    if (looseAvailable < -CONSERVATION_TOLERANCE_CUBIC_METERS) return importNeededRejection(op, subjectRing, looseAvailable)
    val destinationName = op.slots[OpSlot.SPOIL_DESTINATION] ?: HAUL_OFF_DESTINATION
    val surfaceName = proposalSurfaceNameOf(opSeq)
    val surface = Surface.Proposed(surfaceName)
    val digDiff = terrainDiffOf(dig.patch, surface, measured.versionSeq)
    if (destinationName == HAUL_OFF_DESTINATION) {
        val earthwork = earthworkRowOf(surfaceName, dig, loosePlaced = 0.0, haulOff = max(0.0, looseAvailable))
        return conservedRegrade(op, subjectRing, listOf(digDiff), earthwork)
    }
    val spoilRing = regionRingOf(world, destinationName) ?: return slotRejection(op, "spoil-destination-region-missing")
    val spoilRegion = regionCellsOf(measured.grid, spoilRing)
    if (spoilRegion.insideCellCount == 0) return slotRejection(op, "spoil-destination-off-terrain")
    val loosePlaced = max(0.0, looseAvailable)
    val bermDiff = terrainDiffOf(bermPatchOf(measured.grid, spoilRegion, loosePlaced), surface, measured.versionSeq)
    val earthwork = earthworkRowOf(surfaceName, dig, loosePlaced = loosePlaced, haulOff = 0.0)
    return conservedRegrade(op, subjectRing, listOf(digDiff, bermDiff), earthwork)
}

private fun conservedRegrade(
    op: OpRow,
    subjectRing: List<GroundPoint>,
    diffs: List<TerrainDiffRow>,
    earthwork: EarthworkRow,
): PlacementVerdict {
    if (!isConserved(earthwork)) {
        val imbalance = earthwork.bankCutCubicMeters * (1.0 + earthwork.swellFactor) -
            looseEquivalentOfFill(earthwork.compactedFillCubicMeters) -
            earthwork.looseSpoilPlacedCubicMeters - earthwork.haulOffCubicMeters
        return Rejection(RejectionRow(op, listOf(RejectedViolation("earthwork-conservation", centroidOf(subjectRing), imbalance))))
    }
    return Regrade(diffs, earthwork)
}

private fun importNeededRejection(op: OpRow, subjectRing: List<GroundPoint>, looseAvailable: Double): Rejection =
    Rejection(RejectionRow(op, listOf(RejectedViolation("earthwork-import-needed", centroidOf(subjectRing), -looseAvailable))))

private fun looseEquivalentOfFill(compactedFill: Double): Double =
    compactedFill / (1.0 - ASSUMED_SHRINK_FACTOR) * (1.0 + ASSUMED_SWELL_FACTOR)

private fun earthworkRowOf(surfaceName: String, dig: DigPatch, loosePlaced: Double, haulOff: Double): EarthworkRow =
    EarthworkRow(
        surfaceName = surfaceName,
        bankCutCubicMeters = dig.bankCutCubicMeters,
        compactedFillCubicMeters = dig.compactedFillCubicMeters,
        looseSpoilPlacedCubicMeters = loosePlaced,
        haulOffCubicMeters = haulOff,
        topsoilStrippedCubicMeters = 0.0,
        topsoilRespreadCubicMeters = 0.0,
        swellFactor = ASSUMED_SWELL_FACTOR,
        shrinkFactor = ASSUMED_SHRINK_FACTOR,
        factorProvenance = FactorProvenance.ASSUMED,
    )

private fun regionRingOf(world: PlacementWorld, entityName: String): List<GroundPoint>? =
    world.projection.entities[entityName]?.footprint?.takeIf { it.size >= 3 }

private fun groundFormOf(formText: String?): GroundForm? =
    GroundForm.entries.firstOrNull { it.name.equals(formText?.trim(), ignoreCase = true) }

private class RegionCells(
    val firstColumn: Int,
    val firstRow: Int,
    val columns: Int,
    val rows: Int,
    val insideMask: BooleanArray,
) {
    val insideCellCount: Int get() = insideMask.count { it }
}

private class RegionPatch(
    val firstColumn: Int,
    val firstRow: Int,
    val columns: Int,
    val rows: Int,
    val heights: FloatArray,
)

private class DigPatch(
    val patch: RegionPatch,
    val bankCutCubicMeters: Double,
    val compactedFillCubicMeters: Double,
)

private fun regionCellsOf(grid: TerrainGrid, ring: List<GroundPoint>): RegionCells {
    val easts = ring.map { it.east.value }
    val norths = ring.map { it.north.value }
    val firstColumn = (easts.min() / grid.cellSize.value).toInt().coerceIn(0, grid.columns - 1)
    val lastColumn = (easts.max() / grid.cellSize.value).toInt().coerceIn(0, grid.columns - 1)
    val firstRow = (norths.min() / grid.cellSize.value).toInt().coerceIn(0, grid.rows - 1)
    val lastRow = (norths.max() / grid.cellSize.value).toInt().coerceIn(0, grid.rows - 1)
    val columns = lastColumn - firstColumn + 1
    val rows = lastRow - firstRow + 1
    val insideMask = BooleanArray(columns * rows)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val cell = grid.indexOf(firstColumn + column, firstRow + row)
            insideMask[row * columns + column] = isInsidePolygon(grid.centerOf(cell), ring)
        }
    }
    return RegionCells(firstColumn, firstRow, columns, rows, insideMask)
}

private fun digPatchFor(form: GroundForm, grid: TerrainGrid, region: RegionCells): DigPatch? {
    val insideHeights = insideHeightsOf(grid, region)
    val targetHeight = when (form) {
        GroundForm.POND -> insideHeights.min() - POND_DEPTH_METERS
        GroundForm.PAD -> insideHeights.min()
        GroundForm.GRADE -> balancedGradeHeightOf(insideHeights)
        GroundForm.TERRACE, GroundForm.SWALE, GroundForm.BERM -> return null
    }
    val patch = flatTargetPatchOf(grid, region, targetHeight.toFloat())
    val (cut, fill) = cutAndFillOf(grid, region, patch)
    return DigPatch(patch, cut, fill)
}

private fun insideHeightsOf(grid: TerrainGrid, region: RegionCells): DoubleArray {
    val heights = DoubleArray(region.insideCellCount)
    var next = 0
    forEachRegionCell(grid, region) { cell, inside, _ ->
        if (inside) heights[next++] = grid.surfaceHeights[cell].toDouble()
    }
    return heights
}

// balanced grading: pick the plane where bank cut covers the fill's bank equivalent (UGPTI mass-diagram practice)
private fun balancedGradeHeightOf(insideHeights: DoubleArray): Double {
    var low = insideHeights.min()
    var high = insideHeights.max()
    repeat(60) {
        val middle = (low + high) / 2.0
        val cut = insideHeights.sumOf { max(0.0, it - middle) }
        val fillBankEquivalent = insideHeights.sumOf { max(0.0, middle - it) } / (1.0 - ASSUMED_SHRINK_FACTOR)
        if (cut > fillBankEquivalent) low = middle else high = middle
    }
    return low
}

private fun flatTargetPatchOf(grid: TerrainGrid, region: RegionCells, targetHeight: Float): RegionPatch {
    val heights = FloatArray(region.columns * region.rows)
    forEachRegionCell(grid, region) { cell, inside, patchIndex ->
        heights[patchIndex] = if (inside) targetHeight else grid.surfaceHeights[cell]
    }
    return RegionPatch(region.firstColumn, region.firstRow, region.columns, region.rows, heights)
}

private fun bermPatchOf(grid: TerrainGrid, region: RegionCells, looseVolume: Double): RegionPatch {
    val cellArea = grid.cellSize.value * grid.cellSize.value
    val lift = (looseVolume / (region.insideCellCount * cellArea)).toFloat()
    val heights = FloatArray(region.columns * region.rows)
    forEachRegionCell(grid, region) { cell, inside, patchIndex ->
        heights[patchIndex] = grid.surfaceHeights[cell] + if (inside) lift else 0.0f
    }
    return RegionPatch(region.firstColumn, region.firstRow, region.columns, region.rows, heights)
}

private fun cutAndFillOf(grid: TerrainGrid, region: RegionCells, patch: RegionPatch): Pair<Double, Double> {
    val cellArea = grid.cellSize.value * grid.cellSize.value
    var cut = 0.0
    var fill = 0.0
    forEachRegionCell(grid, region) { cell, inside, patchIndex ->
        if (inside) {
            val moved = (grid.surfaceHeights[cell] - patch.heights[patchIndex]).toDouble() * cellArea
            if (moved > 0.0) cut += moved else fill -= moved
        }
    }
    return cut to fill
}

private inline fun forEachRegionCell(grid: TerrainGrid, region: RegionCells, visit: (cell: Int, inside: Boolean, patchIndex: Int) -> Unit) {
    for (row in 0 until region.rows) {
        for (column in 0 until region.columns) {
            val patchIndex = row * region.columns + column
            val cell = grid.indexOf(region.firstColumn + column, region.firstRow + row)
            visit(cell, region.insideMask[patchIndex], patchIndex)
        }
    }
}

private fun terrainDiffOf(patch: RegionPatch, surface: Surface.Proposed, measuredBaselineSeq: Long): TerrainDiffRow =
    TerrainDiffRow(
        firstColumn = patch.firstColumn,
        firstRow = patch.firstRow,
        columns = patch.columns,
        rows = patch.rows,
        heightsBase64 = encodeHeightsBase64(patch.heights),
        surface = surface,
        branchedFromSeq = measuredBaselineSeq,
    )
