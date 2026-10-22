package com.yanyuan.toolbox

import com.yanyuan.toolbox.runtime.ToolboxRuntimeExecutor
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCommandValidationTest {
    @Test
    fun shellCommandMayContainShellSyntaxAndNewlinesWithinBound() {
        assertTrue(ToolboxRuntimeExecutor.validateRequest(
            "printf '%s\\n' ok; echo done", "~/project"
        ) == null)
    }

    @Test
    fun rejectsOversizeCommandAndEscapingWorkdir() {
        assertTrue(ToolboxRuntimeExecutor.validateRequest(
            "x".repeat(9_000), "~"
        ) != null)
        assertTrue(ToolboxRuntimeExecutor.validateRequest(
            "pwd", "~/../outside"
        ) != null)
    }
}
