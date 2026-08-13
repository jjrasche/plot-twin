package plottwin.oppipeline

import plottwin.worldstate.isInsidePolygon
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

private const val COORDINATE_EPSILON = 1e-9

data class RoomExtent(val widthEast: Meters, val depthNorth: Meters)

fun scanCandidateFootprints(
    regionRing: List<GroundPoint>,
    extent: RoomExtent,
    spacing: Meters,
): List<List<GroundPoint>> {
    val regionEasts = regionRing.map { it.east.value }
    val regionNorths = regionRing.map { it.north.value }
    return buildList {
        var anchorEast = regionEasts.min()
        while (anchorEast + extent.widthEast.value <= regionEasts.max() + COORDINATE_EPSILON) {
            var anchorNorth = regionNorths.min()
            while (anchorNorth + extent.depthNorth.value <= regionNorths.max() + COORDINATE_EPSILON) {
                val footprint = rectangleFootprintAt(anchorEast, anchorNorth, extent)
                if (isFootprintInsideRegion(footprint, regionRing)) add(footprint)
                anchorNorth += spacing.value
            }
            anchorEast += spacing.value
        }
    }
}

fun boundingExtentOf(footprint: List<GroundPoint>): RoomExtent = RoomExtent(
    widthEast = Meters(footprint.maxOf { it.east.value } - footprint.minOf { it.east.value }),
    depthNorth = Meters(footprint.maxOf { it.north.value } - footprint.minOf { it.north.value }),
)

private fun rectangleFootprintAt(anchorEast: Double, anchorNorth: Double, extent: RoomExtent): List<GroundPoint> =
    listOf(
        GroundPoint(Meters(anchorEast), Meters(anchorNorth)),
        GroundPoint(Meters(anchorEast + extent.widthEast.value), Meters(anchorNorth)),
        GroundPoint(Meters(anchorEast + extent.widthEast.value), Meters(anchorNorth + extent.depthNorth.value)),
        GroundPoint(Meters(anchorEast), Meters(anchorNorth + extent.depthNorth.value)),
    )

private fun isFootprintInsideRegion(footprint: List<GroundPoint>, regionRing: List<GroundPoint>): Boolean =
    footprint.all { corner -> isInsidePolygon(corner, regionRing) }
