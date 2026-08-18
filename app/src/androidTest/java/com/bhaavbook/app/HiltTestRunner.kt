package com.bhaavbook.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in [HiltTestApplication] so `@HiltAndroidTest` classes get a Dagger
 * test component instead of the real [BhaavBookApplication]. Wired in via
 * `testInstrumentationRunner` in app/build.gradle.kts — every instrumented
 * test in this module runs under it, Hilt or not.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
