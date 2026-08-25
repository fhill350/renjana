package com.fesu.renjana.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstanceLauncherTest {
    @Test
    fun emptyInstanceLaunchRequiresApp() {
        val failure = launchInstanceGateFailure(0)

        assertEquals(
            "No apps added to this instance. Add an app first.",
            failure?.message
        )
    }

    @Test
    fun singleAppInstanceCanUseGenericLaunch() {
        assertNull(launchInstanceGateFailure(1))
    }

    @Test
    fun multiAppInstanceRequiresChoosingApp() {
        val failure = launchInstanceGateFailure(2)

        assertEquals(
            "Multiple apps in this instance. Choose an app to launch.",
            failure?.message
        )
    }
}
