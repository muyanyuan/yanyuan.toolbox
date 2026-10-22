package com.yanyuan.toolbox

import com.yanyuan.toolbox.util.AgentTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentActionParsingTest {
    @Test
    fun parsesShellCommandAsOneExactRequest() {
        val command = "python -c 'print(1 + 1)'"
        val actions = AgentTools.extractActions(
            "```agent\n{\"action\":\"exec\",\"command\":\"$command\",\"cwd\":\"scripts\"}\n```"
        )
        assertEquals(1, actions.size)
        assertEquals("exec", actions.single().action)
        assertEquals(command, actions.single().command)
        assertEquals("scripts", actions.single().cwd)
    }

    @Test
    fun keepsLocalBackendStructuredArgumentsAndNoShellCommand() {
        val actions = AgentTools.extractActions(
            "```agent\n{\"action\":\"exec\",\"program\":\"ls\",\"args\":[\"-la\"],\"cwd\":\".\"}\n```"
        )
        assertEquals(1, actions.size)
        assertEquals("ls", actions.single().program)
        assertEquals(listOf("-la"), actions.single().args)
        assertNull(actions.single().command)
    }
}
