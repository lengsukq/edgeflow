package com.edgeflow.hook

import android.view.KeyEvent
import com.edgeflow.core.model.EditTextCommand
import com.edgeflow.hook.input.TextEditKeyMapping
import kotlin.test.Test
import kotlin.test.assertEquals

class TextEditKeyMappingTest {

    @Test
    fun `basic edit commands map to control shortcuts`() {
        val expected = mapOf(
            EditTextCommand.CUT to KeyEvent.KEYCODE_X,
            EditTextCommand.COPY to KeyEvent.KEYCODE_C,
            EditTextCommand.PASTE to KeyEvent.KEYCODE_V,
            EditTextCommand.SELECT_ALL to KeyEvent.KEYCODE_A,
            EditTextCommand.UNDO to KeyEvent.KEYCODE_Z,
        )

        expected.forEach { (command, keyCode) ->
            val stroke = TextEditKeyMapping.forCommand(command)
            assertEquals(keyCode, stroke.keyCode)
            assertEquals(KeyEvent.META_CTRL_ON, stroke.metaState)
        }
    }

    @Test
    fun `redo maps to control shift z`() {
        val stroke = TextEditKeyMapping.forCommand(EditTextCommand.REDO)

        assertEquals(KeyEvent.KEYCODE_Z, stroke.keyCode)
        assertEquals(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON, stroke.metaState)
    }
}
