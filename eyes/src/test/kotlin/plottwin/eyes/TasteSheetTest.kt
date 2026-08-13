package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.scene3d.Scene3dCameraPose
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import plottwin.capture.readCompiledParcel
import plottwin.render.LooksTaste
import plottwin.render.Rgb
import plottwin.render.SKY_ENTITY_ID
import plottwin.render.SURROUND_BASE_HAZE
import plottwin.render.SURROUND_ENTITY_ID
import plottwin.render.groundDatumOf
import plottwin.render.SceneFrame
import plottwin.render.TERRAIN_ENTITY_ID
import plottwin.render.sceneFrameOf
import plottwin.worldstate.TerrainGrid

// Below this the neighbours' land closes to within the classifier's tolerance of the parcel's own
// ground, which is the one thing the surround exists to prevent; measured, not chosen.
const val SURROUND_HAZE_MOST_LAND = 0.40f
const val SURROUND_HAZE_MORE_MIST = 0.60f
const val SUN_GLOW_TIGHT = 128f
const val SUN_DISK_HOUR = 19
const val SUN_DISK_MINUTE = 30
val OWNER_OFF_AXIS_DEGREES = listOf(0.0, 15.0, 20.0)
val WARM_LAND_TONES = mapOf(
    "stubble" to Rgb(0.52f, 0.45f, 0.26f),
    "dry field" to Rgb(0.48f, 0.42f, 0.28f),
    "mown pasture" to Rgb(0.36f, 0.46f, 0.24f),
)

class TasteSheetTest {

    private fun compiledParcelPath(): Path =
        Path.of(System.getProperty("user.dir"), "..", "capture", "data", "compiled", "parcel.json").normalize()

    @Test
    fun the_taste_sheet_holds_three_questions_each_varying_one_thing() {
        assumeTrue(Files.exists(compiledParcelPath()), "capture cache absent - run capture/scripts/compile_parcel.py first")
        val questions = listOf(surroundQuestion(), sunDiskQuestion(), ownerPoseQuestion())
        val sheet = writeTasteSheet(questions, File(System.getProperty("user.dir"), "build/taste_sheet.png"))
        println("[taste] wrote ${sheet.absolutePath}")
        assertTrue(questions.all { it.panels.size >= 2 }, "a question with one panel is not a comparison")
        assertTrue(
            questions.all { question -> question.panels.map { it.option }.toSet().size == question.panels.size },
            "two panels under one question carry the same label",
        )
    }

    // An option that cannot be told from the parcel is not a surround, it is a second parcel.
    @Test
    fun every_surround_option_on_the_sheet_still_reads_as_not_mine() {
        assumeTrue(Files.exists(compiledParcelPath()), "capture cache absent")
        for (haze in listOf(SURROUND_HAZE_MOST_LAND, SURROUND_BASE_HAZE, SURROUND_HAZE_MORE_MIST)) {
            val scene = realParcelSceneFromFile(compiledParcelPath(), taste = LooksTaste(surroundBaseHaze = haze))
            val gap = surroundPaletteGapOf(scene)
            println(
                "[taste] surround at haze %.2f keeps a palette gap of %d against the parcel (bound %d)"
                    .format(haze, gap, SKY_MATCH_TOLERANCE),
            )
            assertTrue(gap > SKY_MATCH_TOLERANCE, "the surround at haze $haze sits $gap from a colour the parcel draws")
        }
    }

    // Why the sheet offers no farmland: the finding is a claim about this parcel's own palette, so
    // it fails the day a warm surround becomes separable and the option starts existing.
    @Test
    fun no_warm_land_tone_can_be_told_from_the_parcel_it_would_surround() {
        assumeTrue(Files.exists(compiledParcelPath()), "capture cache absent")
        for ((toneName, albedo) in WARM_LAND_TONES) {
            val gap = surroundPaletteGapOf(
                realParcelSceneFromFile(compiledParcelPath(), taste = LooksTaste(surroundAlbedo = albedo)),
            )
            println("[taste] a $toneName surround sits $gap from a colour the parcel draws (bound $SKY_MATCH_TOLERANCE)")
            assertTrue(gap <= SKY_MATCH_TOLERANCE, "a $toneName surround is now separable at $gap - the farmland option exists")
        }
    }

    private fun surroundQuestion(): TasteQuestion {
        val pose = surroundPose()
        return TasteQuestion(
            question = "1. The surround - how much of the neighbouring land do you want to see?",
            heldConstant = "same orbit bearing, same midday sun, same parcel; only the haze the surround starts at moves",
            note = "no farmland option exists: every warm land tone measured lands within 10 of a colour your own parcel draws, and the surround stops reading as not-yours. The whole legal range below spans 11 luma.",
            panels = listOf(
                panelAt(
                    SURROUND_HAZE_MOST_LAND,
                    pose,
                    "most land presence the separation allows (haze 0.40)",
                    "closest the neighbours' ground ever comes to the parcel's own: 12 apart, bound 10",
                ),
                panelAt(
                    SURROUND_BASE_HAZE,
                    pose,
                    "today's mist (haze 0.45)",
                    "reads as fog, not as farmland; buys a 16 gap and the smallest ring steps",
                ),
                panelAt(
                    SURROUND_HAZE_MORE_MIST,
                    pose,
                    "further into mist (haze 0.60)",
                    "the land all but dissolves into the horizon; buys a 19 gap",
                ),
            ),
        )
    }

    private fun panelAt(haze: Float, pose: Scene3dCameraPose, option: String, cost: String): TastePanel {
        val scene = realParcelSceneFromFile(compiledParcelPath(), taste = LooksTaste(surroundBaseHaze = haze))
        val viewer = PlotViewer(scene.spec)
        val luma = surroundLumaInFrame(scene, viewer, pose)
        println("[taste] surround at haze %.2f paints its own pixels at mean luma %.1f".format(haze, luma))
        return TastePanel(option, cost + ", mean luma %.0f".format(luma), viewer.capture(pose))
    }

    private fun surroundPose(): Scene3dCameraPose {
        val scene = realParcelSceneFromFile(compiledParcelPath())
        return plotViewpoints(scene.state).first { it.name == "orbit-1-of-4" }.pose
    }

    private fun sunDiskQuestion(): TasteQuestion {
        val evening = eveningMoment()
        val soft = realParcelSceneFromFile(compiledParcelPath(), evening)
        val tight = realParcelSceneFromFile(compiledParcelPath(), evening, LooksTaste(sunGlowTightness = SUN_GLOW_TIGHT))
        val pose = towardTheSunPose(soft)
        println("[taste] sun-disk moment $evening, sun ${soft.daylight.sun}")
        return TasteQuestion(
            question = "2. The sun - a soft bloom, or a disk with an edge?",
            heldConstant = "same standing point, same evening sun 13 degrees up in the WNW; only the glow's tightness moves",
            note = "the dome is drawn at 288 azimuth steps, so any disk tighter than about 4 degrees across would be a visible polygon.",
            panels = listOf(
                TastePanel(
                    "soft (today): half the glow spread over 47 degrees",
                    "no sun to point at - the whole west sky is bright and the disk has no edge",
                    PlotViewer(soft.spec).capture(pose),
                ),
                TastePanel(
                    "tight: half the glow inside 11.9 degrees",
                    "a rim drawn on a 1.25-degree lattice, and the rest of the west sky loses its warmth",
                    PlotViewer(tight.spec).capture(pose),
                ),
            ),
        )
    }

    private fun eveningMoment(): ZonedDateTime {
        val parcel = readCompiledParcel(compiledParcelPath())
        return REAL_PARCEL_VIEW_DATE.atTime(SUN_DISK_HOUR, SUN_DISK_MINUTE).atZone(ZoneId.of(parcel.site.timeZoneId))
    }

    // looking straight at the sun, so the two glows are compared where they differ
    private fun towardTheSunPose(scene: PlotScene): Scene3dCameraPose {
        val terrain = scene.state.terrain!!.grid
        val frame = sceneFrameOf(terrain)
        val eastMetres = terrain.columns * terrain.cellSize.value / 2.0
        val northMetres = terrain.rows * terrain.cellSize.value * 0.35
        val eyeHeight = groundDatumOf(terrain) + EYE_HEIGHT_METERS
        val reach = (terrain.rows * terrain.cellSize.value).toFloat()
        val toward = scene.daylight.sunDirection
        val eye = Vec3(frame.sceneX(eastMetres), eyeHeight, frame.sceneZ(northMetres))
        return Scene3dCameraPose(
            eye = eye,
            target = Vec3(eye.x + toward.east * reach, eye.y + toward.up * reach, eye.z + toward.north * reach),
        )
    }

    private fun ownerPoseQuestion(): TasteQuestion {
        val scene = realParcelSceneFromFile(compiledParcelPath())
        val terrain = scene.state.terrain!!.grid
        val frame = sceneFrameOf(terrain)
        val viewer = PlotViewer(scene.spec)
        val turn = betterTurnSign(scene, terrain, frame)
        val panels = OWNER_OFF_AXIS_DEGREES.map { degrees ->
            val viewpoint = downTheLengthViewpoint("owner-$degrees", terrain, frame, degrees * turn)
            val mine = ownGroundShareOf(scene, viewer, viewpoint.pose)
            println("[taste] owner pose %.0f degrees off axis: your own ground fills %.3f of the frame".format(degrees, mine))
            TastePanel(
                option = if (degrees == 0.0) "straight down the axis (0 degrees)" else "%.0f degrees off the axis".format(degrees),
                cost = ownerPoseCostOf(degrees) + ", your ground %.0f%% of the frame".format(mine * 100),
                image = viewer.capture(viewpoint.pose),
            )
        }
        return TasteQuestion(
            question = "3. Standing at the road end looking up your land - how far off the axis?",
            heldConstant = "same standing point, same eye height, same level look, same midday sun; only the bearing moves",
            note = "a bearing exactly along the axis cannot fill the frame at any distance, which is why the orbit sweep already runs half a step off it.",
            panels = panels,
        )
    }

    private fun ownerPoseCostOf(degrees: Double): String = when (degrees) {
        0.0 -> "the land is a wedge up the middle and both side lines run to one point"
        15.0 -> "the far end drifts off centre and the near side line leaves the frame"
        else -> "the far end sits well off centre and more neighbouring land is in shot"
    }

    // one side of the axis holds more of the parcel than the other, so only the magnitude differs
    private fun betterTurnSign(scene: PlotScene, terrain: TerrainGrid, frame: SceneFrame): Double {
        val viewer = PlotViewer(scene.spec)
        val shares = listOf(1.0, -1.0).associateWith { sign ->
            ownGroundShareOf(scene, viewer, downTheLengthViewpoint("probe", terrain, frame, 20.0 * sign).pose)
        }
        println("[taste] owner turn one way holds %.3f of the frame, the other %.3f".format(shares.getValue(1.0), shares.getValue(-1.0)))
        return shares.maxBy { it.value }.key
    }
}

fun lumaOf(argb: Int): Double =
    0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)

fun surroundLumaInFrame(scene: PlotScene, viewer: PlotViewer, pose: Scene3dCameraPose): Double {
    val image = viewer.capture(pose)
    val backdrop = scene.spec.meshesByEntity.filterKeys { it == SURROUND_ENTITY_ID }
    val surface = rasterizeVisibleSurfaces(backdrop, viewer.projectorFor(pose))
    val lit = surface.owner.indices.filter { surface.owner[it] != NO_SURFACE }
    return lit.sumOf { lumaOf(image.getRGB(it % surface.width, it / surface.width)) } / lit.size
}

fun ownGroundShareOf(scene: PlotScene, viewer: PlotViewer, pose: Scene3dCameraPose): Double {
    val ground = scene.spec.meshesByEntity.filterKeys { it == TERRAIN_ENTITY_ID }
    val surface = rasterizeVisibleSurfaces(ground, viewer.projectorFor(pose))
    return surface.owner.count { it != NO_SURFACE }.toDouble() / surface.owner.size
}

fun surroundPaletteGapOf(scene: PlotScene): Int {
    val surround = scene.spec.meshesByEntity.getValue(SURROUND_ENTITY_ID).triColors.toSet().map(::argbOfHex)
    val parcel = (scene.spec.meshesByEntity - SURROUND_ENTITY_ID - SKY_ENTITY_ID)
        .values.flatMap { it.triColors }.toSet().map(::argbOfHex)
    return surround.minOf { backdrop -> parcel.minOf { chebyshevBetween(backdrop, it) } }
}
