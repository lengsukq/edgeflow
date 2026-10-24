package com.edgeflow.ui

import com.edgeflow.core.model.PanelConfig

internal const val MIN_CUSTOM_PANEL_DIMENSION = 1
internal const val MAX_CUSTOM_PANEL_DIMENSION = 8
internal const val MAX_CUSTOM_PANEL_NAME_LENGTH = 40

internal sealed interface PanelDraftValidation {
    data class Valid(val name: String, val rows: Int, val columns: Int) : PanelDraftValidation
    data class Invalid(val message: String) : PanelDraftValidation
}

/** Validates a custom panel draft against the grid dimensions supported at runtime. */
internal fun validatePanelDraft(
    name: String,
    rowsText: String,
    columnsText: String,
    panels: List<PanelConfig>,
    editingId: Int? = null,
): PanelDraftValidation {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) {
        return PanelDraftValidation.Invalid("Enter a panel name.")
    }
    if (normalizedName.length > MAX_CUSTOM_PANEL_NAME_LENGTH) {
        return PanelDraftValidation.Invalid("Panel names must be $MAX_CUSTOM_PANEL_NAME_LENGTH characters or fewer.")
    }
    if (panels.any { it.id != editingId && it.name.trim().equals(normalizedName, ignoreCase = true) }) {
        return PanelDraftValidation.Invalid("A panel with this name already exists.")
    }

    val rows = rowsText.toIntOrNull()
        ?: return PanelDraftValidation.Invalid("Rows must be a number from $MIN_CUSTOM_PANEL_DIMENSION to $MAX_CUSTOM_PANEL_DIMENSION.")
    if (rows !in MIN_CUSTOM_PANEL_DIMENSION..MAX_CUSTOM_PANEL_DIMENSION) {
        return PanelDraftValidation.Invalid("Rows must be from $MIN_CUSTOM_PANEL_DIMENSION to $MAX_CUSTOM_PANEL_DIMENSION.")
    }

    val columns = columnsText.toIntOrNull()
        ?: return PanelDraftValidation.Invalid("Columns must be a number from $MIN_CUSTOM_PANEL_DIMENSION to $MAX_CUSTOM_PANEL_DIMENSION.")
    if (columns !in MIN_CUSTOM_PANEL_DIMENSION..MAX_CUSTOM_PANEL_DIMENSION) {
        return PanelDraftValidation.Invalid("Columns must be from $MIN_CUSTOM_PANEL_DIMENSION to $MAX_CUSTOM_PANEL_DIMENSION.")
    }

    return PanelDraftValidation.Valid(normalizedName, rows, columns)
}

/** Allocates an ID after the current nonnegative IDs, avoiding index-based IDs and overflow. */
internal fun nextPanelId(panels: List<PanelConfig>): Int? {
    val usedIds = panels.asSequence().map(PanelConfig::id).filter { it >= 0 }.toHashSet()
    val highestId = usedIds.maxOrNull() ?: return 0
    if (highestId < Int.MAX_VALUE) return highestId + 1

    // This branch is only reachable for imported configurations already using Int.MAX_VALUE.
    var candidate = 0
    while (candidate in usedIds) {
        if (candidate == Int.MAX_VALUE) return null
        candidate++
    }
    return candidate
}

/** Adds a panel or replaces it in place; an edit always retains the saved cell actions. */
internal fun upsertPanel(panels: List<PanelConfig>, panel: PanelConfig): List<PanelConfig> {
    val index = panels.indexOfFirst { it.id == panel.id }
    if (index < 0) return panels + panel
    return panels.toMutableList().also { updated ->
        updated[index] = panel.copy(cells = updated[index].cells)
    }
}

internal fun removePanel(panels: List<PanelConfig>, id: Int): List<PanelConfig> =
    panels.filterNot { it.id == id }
