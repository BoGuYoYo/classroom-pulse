package com.classpulse.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthUiStateTest {
    @Test
    fun successClearsLoadingAndPreviousError() {
        val state = AuthUiState(loading = true, error = "old error")

        val finished = state.afterAuthentication()

        assertEquals(false, finished.loading)
        assertNull(finished.error)
    }

    @Test
    fun failureClearsLoadingAndShowsMessage() {
        val state = AuthUiState(loading = true, error = null)

        val finished = state.afterAuthentication("无法保存登录状态")

        assertEquals(false, finished.loading)
        assertEquals("无法保存登录状态", finished.error)
    }
}
