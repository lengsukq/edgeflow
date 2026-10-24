package com.edgeflow.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityFinishInvokerTest {

    @Test
    fun `invokes four-argument finish method with binder token`() {
        val binder = FakeBinder()
        val data = FakeIntent()
        val service = FourArgumentService()

        assertTrue(ActivityFinishInvoker.finish(service, binder, data))
        assertEquals(binder, service.finishedToken)
        assertEquals(0, service.resultCode)
        assertEquals(data, service.resultData)
        assertEquals(0, service.finishTask)
    }

    @Test
    fun `resolves binder from activity token wrapper`() {
        val binder = FakeBinder()
        val service = ThreeArgumentService()

        assertTrue(ActivityFinishInvoker.finish(service, FakeActivityToken(binder), FakeIntent()))
        assertEquals(binder, service.finishedToken)
    }

    @Test
    fun `returns false when service has no compatible finish method`() {
        assertFalse(ActivityFinishInvoker.finish(UnsupportedService(), FakeBinder(), FakeIntent()))
    }

    @Test
    fun `removes a single-activity task through the framework service`() {
        val service = TaskRemovalService(result = true)

        assertTrue(ActivityFinishInvoker.removeSingleActivityTask(service, 42))
        assertEquals(42, service.removedTaskId)
    }

    @Test
    fun `reports task removal rejected by framework service`() {
        val service = TaskRemovalService(result = false)

        assertFalse(ActivityFinishInvoker.removeSingleActivityTask(service, 42))
    }

    private class FakeBinder

    private class FakeActivityToken(private val binder: FakeBinder) {
        fun asBinder(): FakeBinder = binder
    }

    private class FakeIntent

    private class FourArgumentService {
        var finishedToken: FakeBinder? = null
        var resultCode: Int? = null
        var resultData: FakeIntent? = null
        var finishTask: Int? = null

        fun finishActivity(token: FakeBinder, code: Int, data: FakeIntent, finishTask: Int) {
            finishedToken = token
            resultCode = code
            resultData = data
            this.finishTask = finishTask
        }
    }

    private class ThreeArgumentService {
        var finishedToken: FakeBinder? = null

        fun finishActivity(token: FakeBinder, @Suppress("UNUSED_PARAMETER") code: Int, @Suppress("UNUSED_PARAMETER") data: FakeIntent) {
            finishedToken = token
        }
    }

    private class TaskRemovalService(private val result: Boolean) {
        var removedTaskId: Int? = null

        fun removeTask(taskId: Int): Boolean {
            removedTaskId = taskId
            return result
        }
    }

    private class UnsupportedService {
        fun finishActivity(code: Int) = Unit
    }
}
