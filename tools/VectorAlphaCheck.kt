package com.harleytg.puppyclicker

/** Standalone JVM regression test; no Android SDK or third-party libraries needed. */
fun main() {
    fun check(expected: Int, color: Int, opacity: Float) {
        check(combinedVectorAlpha(color, opacity) == expected) {
            "Unexpected alpha for ${color.toUInt().toString(16)} at $opacity"
        }
    }
    check(255, 0xffffffff.toInt(), 1f)
    check(128, 0x80abcdef.toInt(), 1f)
    check(64, 0x80abcdef.toInt(), 0.5f)
    check(0, 0x00abcdef, 1f)
    check(0, 0xffffffff.toInt(), 0f)
    check(128, 0xffffffff.toInt(), 0.5f)
    for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.1f, 1.1f)) {
        check(runCatching { combinedVectorAlpha(0xffffffff.toInt(), bad) }.isFailure)
    }
    println("PASS: vector opacity regression tests")
}
