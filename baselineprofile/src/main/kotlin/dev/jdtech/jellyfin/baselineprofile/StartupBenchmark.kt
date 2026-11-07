package dev.jdtech.jellyfin.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start and frame timings for the same journey the profile is recorded from.
 *
 * `None` is the worst case (nothing precompiled); `Partial` installs the app's baseline profile,
 * which is what a sideloaded APK gets from profileinstaller. See README.md for how to run it.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun journeyNoCompilation() = journey(CompilationMode.None())

    @Test
    fun journeyBaselineProfile() =
        journey(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun journey(compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            appJourney()
        }
}
