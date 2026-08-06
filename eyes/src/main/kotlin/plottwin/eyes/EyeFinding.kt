package plottwin.eyes

data class EyeFinding(
    val check: String,
    val subject: String,
    val measured: Double,
    val bound: Double,
    val passed: Boolean,
    val detail: String,
) {
    fun line(): String = "${if (passed) "ok " else "FAIL"} $check[$subject] measured=%.3f bound=%.3f $detail".format(measured, bound)
}

fun failedFindings(findings: List<EyeFinding>): List<EyeFinding> = findings.filterNot { it.passed }
