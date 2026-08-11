package plottwin.worldstate

// Captured natural features are ordinary entity rows; the name prefix is the shared
// convention capture writes and the renderer reads (D-007 tree = trunk + canopy).
const val TREE_ENTITY_PREFIX = "tree-"
const val WATER_ENTITY_PREFIX = "pond"
const val ROAD_ENTITY_NAME = "road corridor"

fun isTreeEntity(entityName: String): Boolean = entityName.startsWith(TREE_ENTITY_PREFIX)

fun isWaterEntity(entityName: String): Boolean = entityName.startsWith(WATER_ENTITY_PREFIX)

fun isRoadEntity(entityName: String): Boolean = entityName == ROAD_ENTITY_NAME
