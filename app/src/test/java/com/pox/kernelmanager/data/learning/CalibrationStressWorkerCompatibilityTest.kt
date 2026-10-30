package com.pox.kernelmanager.data.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Regression guard for the calibration crash reported on Android 11 (API 30):
 *
 * `java.lang.Math.fma` was added in Android 13 (API 33). Calling it on an older
 * device throws `NoSuchMethodError` inside a plain worker thread, which kills
 * the whole app the moment "Calibrate Now" is pressed.
 *
 * These tests make sure the stress worker neither references the API-33-only
 * method nor lets a throwable escape its thread.
 */
class CalibrationStressWorkerCompatibilityTest {

    private val workerClass: Class<*>
        get() = Class.forName(
            "com.pox.kernelmanager.data.learning.AdaptiveLearningManager\$StressWorker"
        )

    @Test
    fun stressWorkerDoesNotReferenceApi33MathFma() {
        val resource = "com/pox/kernelmanager/data/learning/" +
            "AdaptiveLearningManager\$StressWorker.class"
        val stream = javaClass.classLoader?.getResourceAsStream(resource)
        assertNotNull("Compiled StressWorker class must be on the unit-test classpath", stream)

        val classBytes = stream!!.use { it.readBytes() }
        // The constant pool stores referenced method names as plain UTF-8.
        val constantPool = String(classBytes, Charsets.ISO_8859_1)
        assertFalse(
            "StressWorker must not call java.lang.Math.fma: it only exists on Android 13+ (API 33)",
            constantPool.contains("fma")
        )
    }

    @Test
    fun stressWorkerCapturesFailuresInsteadOfKillingTheProcess() {
        val active = AtomicBoolean(true)
        val failure = AtomicReference<Throwable?>(null)
        val constructor = workerClass
            .getDeclaredConstructor(AtomicBoolean::class.java, AtomicReference::class.java)
            .apply { isAccessible = true }
        val worker = constructor.newInstance(active, failure) as Runnable

        val uncaught = AtomicReference<Throwable?>(null)
        val thread = Thread(worker, "PoxCalibWorker-test").apply {
            setUncaughtExceptionHandler { _, throwable -> uncaught.set(throwable) }
        }

        thread.start()
        Thread.sleep(100)
        active.set(false)
        thread.join(2_000)

        assertFalse("Stress worker must stop once the calibration cancels it", thread.isAlive)
        assertNull("Stress worker must not leak an uncaught throwable", uncaught.get())
        assertNull("Stress worker must not record a failure during normal operation", failure.get())
    }
}
