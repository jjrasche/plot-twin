package plottwin.eyes

import plottwin.render.WalkableSceneSpec
import plottwin.render.projectWalkableScene
import plottwin.solvers.ToyPlotFixture
import plottwin.solvers.runSolvers
import plottwin.worldstate.CurrentState

data class PlotScene(val state: CurrentState, val spec: WalkableSceneSpec)

fun toyPlotScene(): PlotScene {
    val world = ToyPlotFixture.toyWorld()
    val violations = runSolvers(world, ToyPlotFixture.toyConstraints())
    return PlotScene(world.state, projectWalkableScene(world.state, violations))
}
