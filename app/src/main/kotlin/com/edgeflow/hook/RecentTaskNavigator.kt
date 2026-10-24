package com.edgeflow.hook

/** Minimal task identity used to choose the next eligible app without Android dependencies. */
internal data class RecentTaskCandidate(val taskId: Int, val packageName: String)

internal object RecentTaskNavigator {

    fun nextTaskId(
        tasks: List<RecentTaskCandidate>,
        currentTaskId: Int,
        currentPackageName: String?,
        excludedPackages: Set<String>,
    ): Int? {
        val eligible = tasks.filter { it.packageName.isNotBlank() && it.packageName !in excludedPackages }
        if (currentPackageName in excludedPackages) return eligible.firstOrNull()?.taskId
        if (eligible.size < 2) return null

        val ordered = eligible.sortedBy(RecentTaskCandidate::taskId)
        val currentIndex = ordered.indexOfFirst { it.taskId == currentTaskId }
        return if (currentIndex < 0 || currentIndex == ordered.lastIndex) {
            ordered.first().taskId
        } else {
            ordered[currentIndex + 1].taskId
        }
    }
}
