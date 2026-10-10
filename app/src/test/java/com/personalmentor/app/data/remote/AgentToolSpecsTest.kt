package com.personalmentor.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.personalmentor.app.domain.model.Connector

class AgentToolSpecsTest {
    private val names = AgentToolSpecs.all.map { it.function.name }

    @Test fun toolNamesAreUnique() {
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun agentModeKeepsTaskToolsAndAddsBrowserAndGitHub() {
        assertTrue("create_task" in names)
        assertTrue("browser_open" in names && "browser_click" in names && "browser_type" in names)
        assertTrue("github_read_file" in names && "github_write_file" in names && "github_create_pull_request" in names)
    }

    @Test fun everyRequiredParameterIsDefined() {
        AgentToolSpecs.all.forEach { spec ->
            val params = spec.function.parameters
            val properties = (params["properties"] as kotlinx.serialization.json.JsonObject).keys
            val required = (params["required"] as kotlinx.serialization.json.JsonArray)
                .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            assertTrue("${spec.function.name} requires an undefined parameter", properties.containsAll(required))
        }
    }

    @Test fun scheduledComputerToolsAreOnlyAddedWhenComputerIsSelected() {
        val withoutComputer = AgentToolSpecs.forConnectors(emptySet()).map { it.function.name }
        val withComputer = AgentToolSpecs.forConnectors(emptySet(), cloudComputer = true).map { it.function.name }
        val computerTools = setOf("computer_exec", "computer_read_file", "computer_write_file", "computer_list_files")
        assertTrue(withoutComputer.none { it in computerTools })
        assertTrue(withComputer.containsAll(computerTools))
        assertTrue(AgentToolSpecs.forConnectors(setOf(Connector.GITHUB)).map { it.function.name }.contains("github_read_file"))
    }
}
