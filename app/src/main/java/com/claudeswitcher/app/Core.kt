package com.claudeswitcher.app
object Core {
    init { System.loadLibrary("claude_switcher_core") }
    @JvmStatic external fun apply(state: String, command: String): String
}
