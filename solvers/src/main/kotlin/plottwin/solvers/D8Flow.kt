package plottwin.solvers

import kotlin.math.sqrt

const val NO_FLOW = -1

private val NEIGHBOR_COLUMN_STEPS = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
private val NEIGHBOR_ROW_STEPS = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
private val NEIGHBOR_STEP_LENGTHS = doubleArrayOf(
    sqrt(2.0), 1.0, sqrt(2.0), 1.0, 1.0, sqrt(2.0), 1.0, sqrt(2.0),
)

fun computeFlowTargets(terrain: TerrainGrid): IntArray {
    val flowTargets = IntArray(terrain.cellCount)
    for (row in 0 until terrain.rows) {
        for (column in 0 until terrain.columns) {
            flowTargets[terrain.indexOf(column, row)] = steepestDownslopeNeighbor(terrain, column, row)
        }
    }
    return flowTargets
}

fun computeUpslopeCellCounts(flowTargets: IntArray): IntArray {
    val inflowRemaining = countInflows(flowTargets)
    val drainedOrder = IntArray(flowTargets.size)
    var readyCount = seedHeadwaterCells(inflowRemaining, drainedOrder)
    val upslopeCounts = IntArray(flowTargets.size)
    var cursor = 0
    while (cursor < readyCount) {
        val cell = drainedOrder[cursor++]
        val target = flowTargets[cell]
        if (target == NO_FLOW) continue
        upslopeCounts[target] += upslopeCounts[cell] + 1
        if (--inflowRemaining[target] == 0) drainedOrder[readyCount++] = target
    }
    return upslopeCounts
}

private fun steepestDownslopeNeighbor(terrain: TerrainGrid, column: Int, row: Int): Int {
    val cellHeight = terrain.surfaceHeights[terrain.indexOf(column, row)]
    var steepestGradient = 0.0
    var steepestNeighbor = NO_FLOW
    for (neighbor in 0 until 8) {
        val neighborColumn = column + NEIGHBOR_COLUMN_STEPS[neighbor]
        val neighborRow = row + NEIGHBOR_ROW_STEPS[neighbor]
        if (neighborColumn < 0 || neighborColumn >= terrain.columns) continue
        if (neighborRow < 0 || neighborRow >= terrain.rows) continue
        val neighborIndex = terrain.indexOf(neighborColumn, neighborRow)
        val drop = cellHeight - terrain.surfaceHeights[neighborIndex]
        if (drop <= 0.0f) continue
        val gradient = drop / (NEIGHBOR_STEP_LENGTHS[neighbor] * terrain.cellSize.value)
        if (gradient > steepestGradient) {
            steepestGradient = gradient
            steepestNeighbor = neighborIndex
        }
    }
    return steepestNeighbor
}

private fun countInflows(flowTargets: IntArray): IntArray {
    val inflows = IntArray(flowTargets.size)
    for (target in flowTargets) {
        if (target != NO_FLOW) inflows[target]++
    }
    return inflows
}

private fun seedHeadwaterCells(inflowRemaining: IntArray, drainedOrder: IntArray): Int {
    var seeded = 0
    for (cell in inflowRemaining.indices) {
        if (inflowRemaining[cell] == 0) drainedOrder[seeded++] = cell
    }
    return seeded
}
