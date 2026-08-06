package plottwin.oppipeline

import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

internal fun centroidOf(footprint: List<GroundPoint>): GroundPoint = GroundPoint(
    east = Meters(footprint.sumOf { it.east.value } / footprint.size),
    north = Meters(footprint.sumOf { it.north.value } / footprint.size),
)
