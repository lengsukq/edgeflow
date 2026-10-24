package com.edgeflow.hook.input

import android.view.KeyEvent
import com.edgeflow.core.model.EditTextCommand

internal data class TextEditKeyStroke(
    val keyCode: Int,
    val metaState: Int,
)

internal object TextEditKeyMapping {

    private val control = KeyEvent.META_CTRL_ON

    fun forCommand(command: EditTextCommand): TextEditKeyStroke {
        val keyCode = when (command) {
            EditTextCommand.CUT -> KeyEvent.KEYCODE_X
            EditTextCommand.COPY -> KeyEvent.KEYCODE_C
            EditTextCommand.PASTE -> KeyEvent.KEYCODE_V
            EditTextCommand.SELECT_ALL -> KeyEvent.KEYCODE_A
            EditTextCommand.UNDO, EditTextCommand.REDO -> KeyEvent.KEYCODE_Z
        }
        val metaState = control or if (command == EditTextCommand.REDO) KeyEvent.META_SHIFT_ON else 0
        return TextEditKeyStroke(keyCode, metaState)
    }
}
