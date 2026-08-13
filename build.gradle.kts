// A skipped test is a gate that did not run, and the JUnit XML records it as green either way.
// This repo has already shipped verdicts resting on suites whose skip count was established by a
// human reading numbers off a log, inconsistently: run 5 reported 1 skip, run 8 reported 0. The
// count is now the build's claim, not a reader's, and a skip fails the build that produced it.
subprojects {
    tasks.withType<Test>().configureEach {
        val skipped = mutableListOf<String>()
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit
            override fun beforeTest(test: TestDescriptor) = Unit
            override fun afterTest(test: TestDescriptor, result: TestResult) {
                if (result.resultType == TestResult.ResultType.SKIPPED) skipped += "${test.className}.${test.name}"
            }
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) return
                println(
                    "[gate] ${this@configureEach.path} ran ${result.testCount}: " +
                        "${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped"
                )
            }
        })
        doLast {
            if (skipped.isEmpty()) return@doLast
            throw GradleException(
                "$path skipped ${skipped.size} test(s), so the gate cannot state that it ran:\n" +
                    skipped.joinToString("\n") { "  $it" } +
                    "\nAn absent input is a failure with a cure, never a silent pass - see CaptureCache."
            )
        }
    }
}
