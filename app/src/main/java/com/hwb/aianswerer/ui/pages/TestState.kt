package com.hwb.aianswerer.ui.pages

sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Success(val ms: Long = 0) : TestState()
    data class Error(val msg: String) : TestState()
}
