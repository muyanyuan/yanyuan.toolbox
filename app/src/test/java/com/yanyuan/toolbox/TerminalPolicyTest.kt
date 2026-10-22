package com.yanyuan.toolbox

import com.yanyuan.toolbox.util.TerminalPolicy
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalPolicyTest {
    private val cwd = File(System.getProperty("java.io.tmpdir") ?: ".")

    @Test
    fun allowsReadOnlyDiagnosticCommand() {
        assertNull(TerminalPolicy.validate("ls", listOf("-la"), cwd))
    }

    @Test
    fun rejectsShellSyntaxAndTraversal() {
        assertNotNull(TerminalPolicy.validate("cat", listOf("secret;cat"), cwd))
        assertNotNull(TerminalPolicy.validate("cat", listOf("../outside"), cwd))
        assertNotNull(TerminalPolicy.validate("sh", emptyList(), cwd))
    }

    @Test
    fun rejectsAbsoluteArguments() {
        assertNotNull(TerminalPolicy.validate("cat", listOf("/etc/hosts"), cwd))
    }
}
