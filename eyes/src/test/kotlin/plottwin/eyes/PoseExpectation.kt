package plottwin.eyes

import plottwin.worldstate.CurrentState
import plottwin.worldstate.ROAD_ENTITY_NAME
import plottwin.worldstate.isRoadEntity
import plottwin.worldstate.isTreeEntity
import plottwin.worldstate.isWaterEntity

// Derived from what the log holds, because a pose count is a fact about the plot: Isaac's parcel
// lost its road pose when the property line turned out to exclude W Jolly Rd's right-of-way.
fun expectedPoseCountOf(state: CurrentState): Int {
    val namedSubjects = state.entities.count { (name, placed) ->
        placed.height.value > 0.0 && !isTreeEntity(name) && !isWaterEntity(name) && !isRoadEntity(name)
    }
    val woods = if (state.entities.keys.any(::isTreeEntity)) 1 else 0
    val road = if (state.entities.containsKey(ROAD_ENTITY_NAME)) 1 else 0
    return 1 + ORBIT_STEPS + namedSubjects + woods + road
}
