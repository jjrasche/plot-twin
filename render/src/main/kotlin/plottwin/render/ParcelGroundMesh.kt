package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.TerrainGrid

// The drawn ground silhouette IS the property line. scene3d's batched painter can only draw a
// rectangular heightfield, so the render grid's rows become the ring's own horizontal cuts:
// every vertex sits on or inside the line, and none of the neighbours' land is drawn as ground.
fun parcelGroundMeshOf(
    ring: List<GroundPoint>,
    terrain: TerrainGrid,
    frame: SceneFrame,
    shading: TerrainShading,
    albedoOverride: List<Rgb>? = null,
): Scene3dMesh {
    val cuts = ringCutsAlongNorth(ring, terrain.rows + 1)
    val vertices = groundVerticesOf(cuts, terrain, frame)
    val albedo = albedoOverride ?: grassAlbedoOf(terrain)
    require(albedo.size == terrain.cellCount) { "expected ${terrain.cellCount} albedo cells, got ${albedo.size}" }
    val vertexCountX = terrain.columns + 1
    val triColors = ArrayList<String>(terrain.columns * terrain.rows * 2)
    for (row in 0 until terrain.rows) {
        for (column in 0 until terrain.columns) {
            val corner = row * vertexCountX + column
            val cell = shadedCellOf(cuts, terrain, column, row)
            triColors.add(litTriangleColor(vertices, corner, corner + 1, corner + vertexCountX + 1, cell, albedo, shading))
            triColors.add(litTriangleColor(vertices, corner, corner + vertexCountX + 1, corner + vertexCountX, cell, albedo, shading))
        }
    }
    return Scene3dMesh(
        vertices = vertices,
        triColors = triColors,
        gridCellsX = terrain.columns,
        gridCellsZ = terrain.rows,
    )
}

data class EastCut(val north: Double, val west: Double, val east: Double)

// One interval per row is the whole assumption: true of every convex parcel, and a ring that
// breaks it says so here rather than quietly painting the gap between two lobes as ground.
fun ringCutsAlongNorth(ring: List<GroundPoint>, vertexRows: Int): List<EastCut> {
    require(vertexRows >= 2) { "a ground mesh needs at least two vertex rows, got $vertexRows" }
    val southmost = ring.minOf { it.north.value }
    val northmost = ring.maxOf { it.north.value }
    val depth = northmost - southmost
    val insideEnds = depth * RING_CUT_END_INSET_SHARE
    return List(vertexRows) { row ->
        val north = southmost + depth * row / (vertexRows - 1)
        eastCutAt(ring, north.coerceIn(southmost + insideEnds, northmost - insideEnds))
    }
}

private const val RING_CUT_END_INSET_SHARE = 1e-9

private fun eastCutAt(ring: List<GroundPoint>, north: Double): EastCut {
    val crossings = ArrayList<Double>(2)
    for (vertex in ring.indices) {
        val from = ring[vertex]
        val to = ring[(vertex + 1) % ring.size]
        if (north < minOf(from.north.value, to.north.value)) continue
        if (north >= maxOf(from.north.value, to.north.value)) continue
        val along = (north - from.north.value) / (to.north.value - from.north.value)
        crossings.add(from.east.value + along * (to.east.value - from.east.value))
    }
    require(crossings.size == 2) {
        "a rendered ground row needs one east interval; north $north cuts the ring ${crossings.size} times"
    }
    return EastCut(north, crossings.min(), crossings.max())
}

fun cutGroundPointOf(cut: EastCut, vertexX: Int, columns: Int): GroundPoint = GroundPoint(
    east = Meters(cut.west + (cut.east - cut.west) * vertexX / columns),
    north = Meters(cut.north),
)

private fun groundVerticesOf(cuts: List<EastCut>, terrain: TerrainGrid, frame: SceneFrame): List<Float> {
    val vertices = ArrayList<Float>((terrain.columns + 1) * cuts.size * 3)
    for (cut in cuts) {
        for (vertexX in 0..terrain.columns) {
            val point = cutGroundPointOf(cut, vertexX, terrain.columns)
            vertices.add(frame.sceneX(point.east.value))
            vertices.add(groundHeightAt(terrain, point))
            vertices.add(frame.sceneZ(point.north.value))
        }
    }
    return vertices
}

// Light and colour are measured on the axis-aligned grid the solvers swept, so a warped cell
// reads them where its own centre stands.
private fun shadedCellOf(cuts: List<EastCut>, terrain: TerrainGrid, column: Int, row: Int): Int {
    val south = cuts[row]
    val north = cuts[row + 1]
    val centre = GroundPoint(
        east = Meters(
            (cutGroundPointOf(south, column, terrain.columns).east.value +
                cutGroundPointOf(south, column + 1, terrain.columns).east.value +
                cutGroundPointOf(north, column, terrain.columns).east.value +
                cutGroundPointOf(north, column + 1, terrain.columns).east.value) / 4.0,
        ),
        north = Meters((south.north + north.north) / 2.0),
    )
    return cellIndexAt(terrain, centre)
}

fun cellIndexAt(terrain: TerrainGrid, point: GroundPoint): Int = terrain.indexOf(
    (point.east.value / terrain.cellSize.value).toInt().coerceIn(0, terrain.columns - 1),
    (point.north.value / terrain.cellSize.value).toInt().coerceIn(0, terrain.rows - 1),
)
