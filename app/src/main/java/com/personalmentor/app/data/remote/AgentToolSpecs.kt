package com.personalmentor.app.data.remote

import com.personalmentor.app.domain.agent.BrowserTools
import com.personalmentor.app.domain.agent.GitHubTools
import com.personalmentor.app.domain.agent.MacroTools
import com.personalmentor.app.domain.macro.MacroCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** JSON-schema tool definitions sent to the LLM in Agent Mode: the task tools plus browser and GitHub tools. */
object AgentToolSpecs {

    private const val REPO = "Repository as owner/name"
    private const val BRANCH = "Branch name (default: the repository's default branch)"

    private val browser: List<ToolSpec> = listOf(
        spec(
            BrowserTools.OPEN,
            "Open an http(s) page in the in-app browser. Returns the page text and numbered interactive elements.",
            required = listOf("url"),
            "url" to string("Full URL, e.g. https://github.com"),
        ),
        spec(
            BrowserTools.READ,
            "Read the current page again (text and numbered elements). Use it after the user did something in the Browser tab.",
            required = emptyList(),
        ),
        spec(
            BrowserTools.CLICK,
            "Click an element by its id from the latest page result. Returns the page afterwards (ids change).",
            required = listOf("id"),
            "id" to type("integer", "Element id from the latest page result"),
        ),
        spec(
            BrowserTools.TYPE,
            "Type text into an input/textarea by id. Never use for passwords, card numbers or one-time codes.",
            required = listOf("id", "text"),
            "id" to type("integer", "Element id from the latest page result"),
            "text" to string("Text to enter (replaces the current value)"),
            "submit" to type("boolean", "Press Enter / submit the form afterwards (default false)"),
        ),
        spec(
            BrowserTools.SELECT,
            "Choose an option of a <select> dropdown by its value or visible text.",
            required = listOf("id", "option"),
            "id" to type("integer", "Element id of the <select>"),
            "option" to string("Option value or visible label"),
        ),
        spec(
            BrowserTools.SCROLL,
            "Scroll the page and return it again.",
            required = emptyList(),
            "direction" to string("down (default), up, top or bottom"),
        ),
        spec(BrowserTools.BACK, "Go back one page in the browser history.", required = emptyList()),
    )

    private val github: List<ToolSpec> = listOf(
        spec(
            GitHubTools.LIST_REPOS,
            "List repositories (most recently updated first). Without owner: the signed-in user's repositories.",
            required = emptyList(),
            "owner" to string("Optional user or organisation name"),
        ),
        spec(
            GitHubTools.LIST_FILES,
            "List files and folders at a path of a repository.",
            required = listOf("repo"),
            "repo" to string(REPO),
            "path" to string("Folder path; empty for the repository root"),
            "ref" to string("Branch, tag or commit (default: default branch)"),
        ),
        spec(
            GitHubTools.READ_FILE,
            "Read a text file from a repository. Returns its content and sha.",
            required = listOf("repo", "path"),
            "repo" to string(REPO),
            "path" to string("File path, e.g. app/build.gradle.kts"),
            "ref" to string("Branch, tag or commit (default: default branch)"),
        ),
        spec(
            GitHubTools.SEARCH_CODE,
            "Search code on GitHub, optionally limited to one repository.",
            required = listOf("query"),
            "query" to string("Search terms"),
            "repo" to string("Optional $REPO"),
        ),
        spec(
            GitHubTools.LIST_ISSUES,
            "List issues (pull requests excluded).",
            required = listOf("repo"),
            "repo" to string(REPO),
            "state" to string("open (default), closed or all"),
        ),
        spec(
            GitHubTools.LIST_PULLS,
            "List pull requests.",
            required = listOf("repo"),
            "repo" to string(REPO),
            "state" to string("open (default), closed or all"),
        ),
        spec(
            GitHubTools.WRITE_FILE,
            "Create or update a file with a commit. Send the COMPLETE new file content. The user is asked to approve.",
            required = listOf("repo", "path", "content", "message"),
            "repo" to string(REPO),
            "path" to string("File path"),
            "content" to string("Complete new text of the file"),
            "message" to string("Commit message"),
            "branch" to string(BRANCH),
        ),
        spec(
            GitHubTools.DELETE_FILE,
            "Delete a file with a commit. The user is asked to approve.",
            required = listOf("repo", "path", "message"),
            "repo" to string(REPO),
            "path" to string("File path"),
            "message" to string("Commit message"),
            "branch" to string(BRANCH),
        ),
        spec(
            GitHubTools.CREATE_BRANCH,
            "Create a branch. The user is asked to approve.",
            required = listOf("repo", "name"),
            "repo" to string(REPO),
            "name" to string("New branch name"),
            "from" to string("Branch to start from (default: default branch)"),
        ),
        spec(
            GitHubTools.CREATE_ISSUE,
            "Open an issue. The user is asked to approve.",
            required = listOf("repo", "title"),
            "repo" to string(REPO),
            "title" to string("Issue title"),
            "body" to string("Issue text (markdown)"),
            "labels" to string("Optional comma-separated labels"),
        ),
        spec(
            GitHubTools.COMMENT,
            "Comment on an issue or pull request. The user is asked to approve.",
            required = listOf("repo", "number", "body"),
            "repo" to string(REPO),
            "number" to type("integer", "Issue or pull request number"),
            "body" to string("Comment text (markdown)"),
        ),
        spec(
            GitHubTools.CREATE_PULL_REQUEST,
            "Open a pull request from an existing branch. The user is asked to approve.",
            required = listOf("repo", "title", "head"),
            "repo" to string(REPO),
            "title" to string("Pull request title"),
            "head" to string("Branch that contains the changes"),
            "base" to string("Branch to merge into (default: default branch)"),
            "body" to string("Description (markdown)"),
            "draft" to type("boolean", "Open as draft (default false)"),
        ),
        spec(
            GitHubTools.MERGE_PULL_REQUEST,
            "Merge a pull request. The user is asked to approve.",
            required = listOf("repo", "number"),
            "repo" to string(REPO),
            "number" to type("integer", "Pull request number"),
            "method" to string("squash (default), merge or rebase"),
        ),
    )

    private val itemList: JsonObject = buildJsonObject {
        put("type", "array")
        put(
            "items",
            buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put("type", string("Type id from the catalog"))
                        put("params", type("object", "Parameter values by key, as strings"))
                    },
                )
                put("required", JsonArray(listOf(JsonPrimitive("type"))))
            },
        )
    }

    private val macros: List<ToolSpec> = listOf(
        spec(MacroTools.LIST, "List the user's macros (automations) with their triggers, actions and constraints.", required = emptyList()),
        spec(
            MacroTools.CREATE,
            "Create a macro: when a trigger fires and all constraints hold, the actions run in order. " +
                "Use trigger type manual for a macro that only runs on request. The user is asked to approve. " +
                "Catalog (type(param=options)):\n" + MacroCatalog.describeForAgent() +
                "\nIn texts, {battery}, {time}, {date}, {trigger}, {http_response}, {http_code} and local variables can be used.",
            required = listOf("name", "triggers", "actions"),
            "name" to string("Unique macro name"),
            "triggers" to itemList,
            "actions" to itemList,
            "constraints" to itemList,
            "category" to string("Optional category folder name"),
        ),
        spec(
            MacroTools.RUN, "Run a macro now by name (its constraints still apply). The user is asked to approve.",
            required = listOf("name"), "name" to string("Macro name"),
        ),
        spec(
            MacroTools.SET_ENABLED, "Switch a macro on or off. The user is asked to approve.",
            required = listOf("name", "enabled"),
            "name" to string("Macro name"), "enabled" to type("boolean", "true to enable, false to disable"),
        ),
        spec(
            MacroTools.DELETE, "Delete a macro. The user is asked to approve.",
            required = listOf("name"), "name" to string("Macro name"),
        ),
    )

    val all: List<ToolSpec> = TaskToolSpecs.all + browser + github + macros

    private fun string(description: String) = type("string", description)

    private fun type(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun spec(
        name: String,
        description: String,
        required: List<String>,
        vararg properties: Pair<String, JsonObject>,
    ) = ToolSpec(
        function = FunctionSpec(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { properties.forEach { (key, value) -> put(key, value) } })
                put("required", JsonArray(required.map(::JsonPrimitive)))
            },
        ),
    )
}
