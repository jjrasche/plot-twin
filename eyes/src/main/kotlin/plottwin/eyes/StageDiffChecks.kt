package plottwin.eyes

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import plottwin.render.CUBIC_YARDS_PER_CUBIC_METER
import plottwin.render.DiffRegionKind
import plottwin.render.StageDiffRender
import plottwin.render.StageDiffSpec
import plottwin.render.paintTopDownBase
import plottwin.render.stageDiffLegendOf
import plottwin.worldstate.EarthworkRow
import plottwin.worldstate.EarthworkTotals
import plottwin.worldstate.LoggedRow
import plottwin.worldstate.projectEarthworkLedger

const val DIFF_REGION_IOU_FLOOR = 0.9
const val QUIET_GROUND_MAX_DIFFER_SHARE = 0.005
// half of the 0.35 minimum tint's red-blue separation (~48/255), safely above base ground's zero
const val RAMP_CLASSIFY_MARGIN = 24
const val LEGEND_DISPLAY_ROUNDING_CUBIC_YARDS = 0.05

fun stageDiffFindings(scene: StageDiffScene, spec: StageDiffSpec, render: StageDiffRender): List<EyeFinding> {
    val findings = ArrayList<EyeFinding>()
    findings += diffRegionFindings(scene.stageName, spec, render)
    findings += legendEqualsLedgerFinding(scene, spec)
    findings += quietGroundFinding(scene.stageName, spec, render)
    return findings
}

// gate 1: the pixels painted as cut/fill are the cells the proposal actually wrote, per region kind
private fun diffRegionFindings(subject: String, spec: StageDiffSpec, render: StageDiffRender): List<EyeFinding> {
    val kindsPresent = spec.regions.map { it.kind }.distinct()
    return kindsPresent.map { kind ->
        val expected = expectedKindMask(spec, kind, render)
        val observed = observedKindMask(render, kind)
        val iou = intersectionOverUnion(expected, observed)
        EyeFinding(
            check = "stage-diff-region-${kind.name.lowercase()}",
            subject = subject,
            measured = iou,
            bound = DIFF_REGION_IOU_FLOOR,
            passed = iou >= DIFF_REGION_IOU_FLOOR,
            detail = "painted ${kind.name.lowercase()} pixels vs the proposal's terrain-diff cells",
        )
    }
}

private fun expectedKindMask(spec: StageDiffSpec, kind: DiffRegionKind, render: StageDiffRender): BooleanArray {
    val mask = BooleanArray(render.plotWidthPx * render.plotHeightPx)
    for (pixelY in 0 until spec.rows) {
        val row = spec.rows - 1 - pixelY
        for (pixelX in 0 until spec.columns) {
            val pixel = pixelY * spec.columns + pixelX
            if (render.overlayMask[pixel]) continue
            val delta = spec.deltaMeters[row * spec.columns + pixelX]
            mask[pixel] = if (kind == DiffRegionKind.CUT) delta < 0.0f else delta > 0.0f
        }
    }
    return mask
}

private fun observedKindMask(render: StageDiffRender, kind: DiffRegionKind): BooleanArray {
    val mask = BooleanArray(render.plotWidthPx * render.plotHeightPx)
    for (pixelY in 0 until render.plotHeightPx) {
        for (pixelX in 0 until render.plotWidthPx) {
            val pixel = pixelY * render.plotWidthPx + pixelX
            if (render.overlayMask[pixel]) continue
            val color = Color(render.image.getRGB(pixelX, pixelY))
            val redness = color.red - color.blue
            mask[pixel] =
                if (kind == DiffRegionKind.CUT) redness > RAMP_CLASSIFY_MARGIN
                else redness < -RAMP_CLASSIFY_MARGIN
        }
    }
    return mask
}

// gate 2: the legend carries exactly the ledger's numbers — re-projected from the raw log, not from the spec
private fun legendEqualsLedgerFinding(scene: StageDiffScene, spec: StageDiffSpec): EyeFinding {
    val independentLedger = ledgerOfProposal(scene.history, scene.proposal.name)
    val expectedLegend = stageDiffLegendOf(independentLedger)
    val parsedDiscrepancy = maxOf(
        parsedYardsDiscrepancy(spec.legend.dugLine, independentLedger.bankCutCubicMeters),
        parsedYardsDiscrepancy(spec.legend.placedLine, independentLedger.looseSpoilPlacedCubicMeters),
        parsedYardsDiscrepancy(spec.legend.hauledLine, independentLedger.haulOffCubicMeters),
    )
    val passed = spec.legend == expectedLegend && parsedDiscrepancy <= LEGEND_DISPLAY_ROUNDING_CUBIC_YARDS
    return EyeFinding(
        check = "stage-diff-legend-equals-ledger",
        subject = scene.stageName,
        measured = parsedDiscrepancy,
        bound = LEGEND_DISPLAY_ROUNDING_CUBIC_YARDS,
        passed = passed,
        detail = if (spec.legend == expectedLegend) "legend text equals the ledger's, worst parsed drift in yd3 shown"
        else "legend text diverges from the ledger: got ${spec.legend}, ledger says $expectedLegend",
    )
}

private fun ledgerOfProposal(history: List<LoggedRow>, proposalName: String): EarthworkTotals =
    projectEarthworkLedger(
        history.filter { logged -> (logged.row as? EarthworkRow)?.surfaceName == proposalName }
    ).plot

private fun parsedYardsDiscrepancy(legendLine: String, ledgerCubicMeters: Double): Double {
    val shown = requireNotNull(Regex("""\d+(\.\d+)?""").find(legendLine)) { "no number in legend line: $legendLine" }
        .value.toDouble()
    return abs(shown - ledgerCubicMeters * CUBIC_YARDS_PER_CUBIC_METER)
}

// gate 3: outside diff regions and link graphics the view is the plain base render — no invented noise
private fun quietGroundFinding(subject: String, spec: StageDiffSpec, render: StageDiffRender): EyeFinding {
    val base = paintTopDownBase(spec)
    var quietPixels = 0
    var differing = 0
    for (pixelY in 0 until render.plotHeightPx) {
        for (pixelX in 0 until render.plotWidthPx) {
            val pixel = pixelY * render.plotWidthPx + pixelX
            if (render.diffMask[pixel] || render.overlayMask[pixel]) continue
            quietPixels++
            if (render.image.getRGB(pixelX, pixelY) != base.getRGB(pixelX, pixelY)) differing++
        }
    }
    val share = if (quietPixels == 0) 0.0 else differing.toDouble() / quietPixels
    return EyeFinding(
        check = "stage-diff-quiet-ground",
        subject = subject,
        measured = share,
        bound = QUIET_GROUND_MAX_DIFFER_SHARE,
        passed = share <= QUIET_GROUND_MAX_DIFFER_SHARE,
        detail = "$differing of $quietPixels pixels outside diff+links differ from the plain base render",
    )
}
