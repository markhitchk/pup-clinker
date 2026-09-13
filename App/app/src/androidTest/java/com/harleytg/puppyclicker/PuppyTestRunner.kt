package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation uses a plain Application so unrelated production startup services cannot crash
 * the test process before JUnit discovers tests. Individual startup components are invoked
 * explicitly by their own instrumentation smoke tests.
 */
class PuppyTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
