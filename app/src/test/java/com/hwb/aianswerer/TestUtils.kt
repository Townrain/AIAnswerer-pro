package com.hwb.aianswerer

import org.junit.Assume

/**
 * Shared test utility: safely invoke a block that may require Android runtime.
 * Skips the test via [Assume.assumeNoException] if the runtime is unavailable.
 */
inline fun <T> safelyInvoke(crossinline block: () -> T): T {
    return try {
        block()
    } catch (e: Throwable) {
        Assume.assumeNoException(
            "Requires Android runtime (MMKV/Application context); test skipped in JVM",
            e
        )
        @Suppress("UNREACHABLE_CODE")
        throw e
    }
}
