package com.edgeflow.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImmersivePolicyTest {
    @Test fun createsAndRestoresOnlyOwnedTokens() {
        val a = ImmersivePolicy.update("some_option=x", ImmersivePolicy.Bar.STATUS, true)
        assertEquals("some_option=x:immersive.status=*", a)
        val b = ImmersivePolicy.update(a, ImmersivePolicy.Bar.NAVIGATION, true)
        assertEquals("some_option=x:immersive.status=*:immersive.navigation=*", b)
        val c = ImmersivePolicy.update(b, ImmersivePolicy.Bar.STATUS, false)
        assertEquals("some_option=x:immersive.navigation=*", c)
        assertEquals("some_option=x", ImmersivePolicy.update(c, ImmersivePolicy.Bar.NAVIGATION, false))
    }

    @Test fun refusesToClobberExistingPolicies() {
        assertNull(ImmersivePolicy.update("immersive.full=*", ImmersivePolicy.Bar.STATUS, true))
        assertNull(ImmersivePolicy.update("immersive.status=apps", ImmersivePolicy.Bar.STATUS, true))
        assertNull(ImmersivePolicy.update("immersive.navigation=*", ImmersivePolicy.Bar.NAVIGATION, true))
    }
}
