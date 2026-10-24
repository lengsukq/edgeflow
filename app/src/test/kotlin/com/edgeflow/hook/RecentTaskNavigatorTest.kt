package com.edgeflow.hook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecentTaskNavigatorTest {

    private val excluded = setOf("com.edgeflow", "android", "com.android.systemui")

    @Test
    fun `selects the next task in reference task-id order`() {
        val tasks = listOf(
            RecentTaskCandidate(8, "com.example.c"),
            RecentTaskCandidate(2, "com.example.a"),
            RecentTaskCandidate(5, "com.example.b"),
        )

        assertEquals(5, RecentTaskNavigator.nextTaskId(tasks, 2, "com.example.a", excluded))
    }

    @Test
    fun `wraps to the first eligible task`() {
        val tasks = listOf(
            RecentTaskCandidate(5, "com.example.b"),
            RecentTaskCandidate(2, "com.example.a"),
        )

        assertEquals(2, RecentTaskNavigator.nextTaskId(tasks, 5, "com.example.b", excluded))
    }

    @Test
    fun `starts at the first eligible task when the module is foreground`() {
        val tasks = listOf(
            RecentTaskCandidate(9, "com.edgeflow"),
            RecentTaskCandidate(4, "com.example.a"),
        )

        assertEquals(4, RecentTaskNavigator.nextTaskId(tasks, 9, "com.edgeflow", excluded))
    }

    @Test
    fun `does not switch when there is no other eligible task`() {
        assertNull(
            RecentTaskNavigator.nextTaskId(
                listOf(RecentTaskCandidate(4, "com.example.a")),
                4,
                "com.example.a",
                excluded,
            ),
        )
    }
}
