package plottwin.worldstate

data class LoggedEarthwork(
    val seq: Long,
    val opSeq: Long?,
    val row: EarthworkRow,
)

data class EarthworkTotals(
    val bankCutCubicMeters: Double,
    val compactedFillCubicMeters: Double,
    val looseSpoilPlacedCubicMeters: Double,
    val haulOffCubicMeters: Double,
    val topsoilStrippedCubicMeters: Double,
    val topsoilRespreadCubicMeters: Double,
) {
    companion object {
        val ZERO = EarthworkTotals(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class EarthworkLedger(
    val perOp: Map<Long, EarthworkTotals>,
    val plot: EarthworkTotals,
)

fun earthworkLedgerOf(earthworks: List<LoggedEarthwork>): EarthworkLedger {
    val perOp = earthworks
        .groupBy { it.opSeq ?: it.seq }
        .mapValues { (_, entries) -> totalsOf(entries.map { it.row }) }
    return EarthworkLedger(perOp = perOp, plot = totalsOf(earthworks.map { it.row }))
}

fun projectEarthworkLedger(log: List<LoggedRow>): EarthworkLedger =
    earthworkLedgerOf(
        log.mapNotNull { logged ->
            (logged.row as? EarthworkRow)?.let { LoggedEarthwork(logged.seq, causeOpSeqOf(logged), it) }
        }
    )

private fun totalsOf(rows: List<EarthworkRow>): EarthworkTotals =
    rows.fold(EarthworkTotals.ZERO) { totals, row ->
        EarthworkTotals(
            bankCutCubicMeters = totals.bankCutCubicMeters + row.bankCutCubicMeters,
            compactedFillCubicMeters = totals.compactedFillCubicMeters + row.compactedFillCubicMeters,
            looseSpoilPlacedCubicMeters = totals.looseSpoilPlacedCubicMeters + row.looseSpoilPlacedCubicMeters,
            haulOffCubicMeters = totals.haulOffCubicMeters + row.haulOffCubicMeters,
            topsoilStrippedCubicMeters = totals.topsoilStrippedCubicMeters + row.topsoilStrippedCubicMeters,
            topsoilRespreadCubicMeters = totals.topsoilRespreadCubicMeters + row.topsoilRespreadCubicMeters,
        )
    }
