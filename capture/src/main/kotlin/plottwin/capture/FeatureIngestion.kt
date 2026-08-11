package plottwin.capture

import kotlin.math.cos
import kotlin.math.sin
import plottwin.worldstate.EntityRow
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.ROAD_ENTITY_NAME
import plottwin.worldstate.TREE_ENTITY_PREFIX
import plottwin.worldstate.WATER_ENTITY_PREFIX
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

const val CROWN_POLYGON_CORNERS = 12

// Lidar-measured features enter the log as CAPTURE entity rows (D-007, D-013): a tree row
// carries the crown circle as its footprint and the full height; the renderer rebuilds the
// trunk cylinder + canopy ellipsoid from exactly that.
fun appendParcelFeatures(log: WorldLog, features: ParcelFeatures): List<Long> {
    val seqs = ArrayList<Long>()
    features.trees.forEachIndexed { index, tree ->
        seqs += log.append(
            EntityRow(
                entityName = treeEntityName(index),
                footprint = crownFootprintOf(tree),
                height = Meters(tree.heightMeters),
            ),
            WriterRole.CAPTURE,
        )
    }
    features.structures.forEachIndexed { index, structure ->
        seqs += log.append(
            EntityRow(
                entityName = "structure-%02d".format(index + 1),
                footprint = structure.footprint.map(::groundPointOf),
                height = Meters(structure.heightMeters),
            ),
            WriterRole.CAPTURE,
        )
    }
    features.water.forEachIndexed { index, water ->
        seqs += log.append(
            EntityRow(
                entityName = if (index == 0) WATER_ENTITY_PREFIX else "$WATER_ENTITY_PREFIX-${index + 1}",
                footprint = water.footprint.map(::groundPointOf),
                height = Meters(0.0),
            ),
            WriterRole.CAPTURE,
        )
    }
    features.road.forEach { road ->
        seqs += log.append(
            EntityRow(
                entityName = ROAD_ENTITY_NAME,
                footprint = road.footprint.map(::groundPointOf),
                height = Meters(0.0),
            ),
            WriterRole.CAPTURE,
        )
    }
    return seqs
}

fun treeEntityName(index: Int): String = TREE_ENTITY_PREFIX + "%03d".format(index + 1)

fun crownFootprintOf(tree: FeatureTree): List<GroundPoint> = List(CROWN_POLYGON_CORNERS) { corner ->
    val angle = 2.0 * Math.PI * corner / CROWN_POLYGON_CORNERS
    GroundPoint(
        east = Meters(tree.eastMeters + tree.crownRadiusMeters * cos(angle)),
        north = Meters(tree.northMeters + tree.crownRadiusMeters * sin(angle)),
    )
}

private fun groundPointOf(point: FeaturePoint): GroundPoint =
    GroundPoint(east = Meters(point.eastMeters), north = Meters(point.northMeters))
