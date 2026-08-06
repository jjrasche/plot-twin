package plottwin.worldstate

fun causeOpSeqOf(logged: LoggedRow): Long? =
    logged.refs.firstOrNull { it.role == RefRole.OP }?.seq

fun resolutionSeqOf(logged: LoggedRow): Long? =
    logged.refs.firstOrNull { it.role == RefRole.RESOLUTION }?.seq

fun resolutionOf(log: List<LoggedRow>, opSeq: Long): LoggedRow? {
    val status = log.firstOrNull { it.row is OpStatusRow && causeOpSeqOf(it) == opSeq } ?: return null
    val resolutionSeq = resolutionSeqOf(status) ?: return null
    return log.firstOrNull { it.seq == resolutionSeq }
}
