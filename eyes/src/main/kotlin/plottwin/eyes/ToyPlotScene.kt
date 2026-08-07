package plottwin.eyes

import java.time.ZonedDateTime
import plottwin.render.Daylight
import plottwin.render.WalkableSceneSpec
import plottwin.render.daylightOverPlot
import plottwin.render.projectWalkableScene
import plottwin.solvers.ToyPlotFixture
import plottwin.solvers.runSolvers
import plottwin.worldstate.CurrentState

data class PlotScene(val state: CurrentState, val spec: WalkableSceneSpec, val daylight: Daylight)

fun toyPlotScene(moment: ZonedDateTime = ToyPlotFixture.toyEvening): PlotScene {
    val world = ToyPlotFixture.toyWorld()
    val violations = runSolvers(world, ToyPlotFixture.toyConstraints())
    val daylight = daylightOverPlot(world.state, moment)
    return PlotScene(world.state, projectWalkableScene(world.state, violations, daylight), daylight)
}
