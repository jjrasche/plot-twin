package plottwin.eyes

import java.time.ZonedDateTime
import plottwin.render.Daylight
import plottwin.render.WalkableSceneSpec
import plottwin.render.daylightOverPlot
import plottwin.render.projectWalkableScene
import plottwin.render.withSkyDome
import plottwin.solvers.Constraint
import plottwin.solvers.SolverWorld
import plottwin.solvers.ToyPlotFixture
import plottwin.solvers.runSolvers
import plottwin.worldstate.CurrentState

data class PlotScene(val state: CurrentState, val spec: WalkableSceneSpec, val daylight: Daylight)

fun plotSceneOf(world: SolverWorld, constraints: List<Constraint>, moment: ZonedDateTime): PlotScene {
    val violations = runSolvers(world, constraints)
    val daylight = daylightOverPlot(world.state, moment)
    val terrain = requireNotNull(world.state.terrain) { "a plot scene needs a base-terrain row" }.grid
    val spec = withSkyDome(projectWalkableScene(world.state, violations, daylight), terrain, daylight)
    return PlotScene(world.state, spec, daylight)
}

fun toyPlotScene(moment: ZonedDateTime = ToyPlotFixture.toyEvening): PlotScene =
    plotSceneOf(ToyPlotFixture.toyWorld(), ToyPlotFixture.toyConstraints(), moment)
