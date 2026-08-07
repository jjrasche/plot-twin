package plottwin.eyes

data class EyeFinding(
    val check: String,
    val subject: String,
    val measured: Double,
    val bound: Double,
    val passed: Boolean,
    val detail: String,
    val advisory: Boolean = false,
) {
    fun line(): String =
        "${verdict()} $check[$subject] measured=%.3f bound=%.3f $detail".format(measured, bound)

    private fun verdict(): String = when {
        advisory -> "ADV "
        passed -> "ok  "
        else -> "FAIL"
    }
}

fun failedFindings(findings: List<EyeFinding>): List<EyeFinding> =
    findings.filterNot { it.advisory || it.passed }

fun advisoryFindings(findings: List<EyeFinding>): List<EyeFinding> = findings.filter { it.advisory }
