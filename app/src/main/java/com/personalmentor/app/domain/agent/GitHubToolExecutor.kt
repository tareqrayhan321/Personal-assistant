package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.github.GitHubApi
import com.personalmentor.app.domain.github.GitHubException
import com.personalmentor.app.domain.github.GitHubResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import javax.inject.Inject

/** Tool names of the GitHub integration exposed to the LLM in Agent Mode. */
object GitHubTools {
    const val LIST_REPOS = "github_list_repos"
    const val LIST_FILES = "github_list_files"
    const val READ_FILE = "github_read_file"
    const val SEARCH_CODE = "github_search_code"
    const val LIST_ISSUES = "github_list_issues"
    const val LIST_PULLS = "github_list_pulls"
    const val WRITE_FILE = "github_write_file"
    const val DELETE_FILE = "github_delete_file"
    const val CREATE_BRANCH = "github_create_branch"
    const val CREATE_ISSUE = "github_create_issue"
    const val COMMENT = "github_comment"
    const val CREATE_PULL_REQUEST = "github_create_pull_request"
    const val MERGE_PULL_REQUEST = "github_merge_pull_request"
}

/**
 * Runs `github_*` tool calls through the GitHub REST API. Reads run freely; every change (commit, branch, issue,
 * comment, PR, merge, delete) first goes through [ActionApprover] with a readable summary.
 * Repo names, file paths and branch names are validated so a model (or a prompt injection) cannot steer a
 * request to another API endpoint with `..` segments.
 */
class GitHubToolExecutor @Inject constructor(
    private val api: GitHubApi,
    private val approver: ActionApprover,
) {
    suspend fun execute(name: String, args: JsonObject): String = try {
        when (name) {
            GitHubTools.LIST_REPOS -> listRepos(args)
            GitHubTools.LIST_FILES -> listFiles(args)
            GitHubTools.READ_FILE -> readFile(args)
            GitHubTools.SEARCH_CODE -> searchCode(args)
            GitHubTools.LIST_ISSUES -> listIssues(args)
            GitHubTools.LIST_PULLS -> listPulls(args)
            GitHubTools.WRITE_FILE -> writeFile(args)
            GitHubTools.DELETE_FILE -> deleteFile(args)
            GitHubTools.CREATE_BRANCH -> createBranch(args)
            GitHubTools.CREATE_ISSUE -> createIssue(args)
            GitHubTools.COMMENT -> comment(args)
            GitHubTools.CREATE_PULL_REQUEST -> createPullRequest(args)
            GitHubTools.MERGE_PULL_REQUEST -> mergePullRequest(args)
            else -> toolFail("Unknown tool: $name")
        }
    } catch (e: GitHubException) {
        toolFail(e.message ?: "GitHub request failed")
    }

    // ---- reads ----

    private suspend fun listRepos(args: JsonObject): String {
        val owner = args.str("owner")?.trim().orEmpty()
        val segments = when {
            owner.isEmpty() -> listOf("user", "repos")
            OWNER.matches(owner) -> listOf("users", owner, "repos")
            else -> return toolFail("Invalid owner")
        }
        val items = requireOk(api.call("GET", segments, mapOf("sort" to "updated", "per_page" to "30"))).asArray()
        val repos = items.orEmpty().mapNotNull {
            it.asObject()?.pick("full_name", "private", "description", "default_branch", "updated_at")
        }
        return toolOk { put("repos", JsonArray(repos)) }
    }

    private suspend fun listFiles(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val pathSegments = optionalPath(args.str("path")) ?: return toolFail(BAD_PATH)
        val ref = refArg(args) ?: return toolFail(BAD_BRANCH)
        val body = requireOk(
            api.call("GET", repo.segments("contents") + pathSegments, ref.query()),
        )
        val items = body.asArray() ?: return toolFail("That path is a file; use github_read_file.")
        val files = items.mapNotNull { it.asObject()?.pick("name", "path", "type", "size") }
        return toolOk { put("files", JsonArray(files)) }
    }

    private suspend fun readFile(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val pathSegments = filePath(args.str("path")) ?: return toolFail(BAD_PATH)
        val ref = refArg(args) ?: return toolFail(BAD_BRANCH)
        val body = requireOk(api.call("GET", repo.segments("contents") + pathSegments, ref.query())).asObject()
            ?: return toolFail("That path is a directory; use github_list_files.")
        if (body.str("type") != "file") return toolFail("That path is not a regular file.")
        val encoded = body.str("content")
        val size = body.long("size") ?: 0L
        if (encoded.isNullOrEmpty()) {
            return if (size == 0L) toolOk { put("content", ""); put("sha", body.str("sha").orEmpty()) }
            else toolFail("File is larger than 1 MB, which this API cannot return.")
        }
        val text = String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8)
        if (text.take(BINARY_SCAN).contains('\u0000')) return toolFail("This looks like a binary file; it cannot be shown as text.")
        val truncated = text.length > MAX_FILE_CHARS
        return toolOk {
            put("path", pathSegments.joinToString("/"))
            put("sha", body.str("sha").orEmpty())
            put("content", if (truncated) text.take(MAX_FILE_CHARS) else text)
            if (truncated) put("truncated", "Only the first $MAX_FILE_CHARS of ${text.length} characters are shown.")
        }
    }

    private suspend fun searchCode(args: JsonObject): String {
        val query = args.str("query")?.trim().orEmpty()
        if (query.isEmpty()) return toolFail("query is required")
        val repoArg = args.str("repo")?.trim().orEmpty()
        val repo = if (repoArg.isEmpty()) null else parseRepo(repoArg) ?: return toolFail(BAD_REPO)
        val q = if (repo != null) "$query repo:${repo.full}" else query
        val body = requireOk(api.call("GET", listOf("search", "code"), mapOf("q" to q, "per_page" to "10"))).asObject()
        val results = body?.get("items").asArray().orEmpty().mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            buildJsonObject {
                item.pick("name", "path", "html_url").forEach { (key, value) -> put(key, value) }
                item["repository"].asObject()?.str("full_name")?.let { put("repo", it) }
            }
        }
        return toolOk { put("results", JsonArray(results)) }
    }

    private suspend fun listIssues(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val state = stateArg(args) ?: return toolFail(BAD_STATE)
        val items = requireOk(api.call("GET", repo.segments("issues"), mapOf("state" to state, "per_page" to "30"))).asArray()
        val issues = items.orEmpty().mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            if (item.containsKey("pull_request")) null // the issues endpoint also returns PRs
            else item.pick("number", "title", "state", "html_url", "comments", "updated_at")
        }
        return toolOk { put("issues", JsonArray(issues)) }
    }

    private suspend fun listPulls(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val state = stateArg(args) ?: return toolFail(BAD_STATE)
        val items = requireOk(api.call("GET", repo.segments("pulls"), mapOf("state" to state, "per_page" to "30"))).asArray()
        val pulls = items.orEmpty().mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            buildJsonObject {
                item.pick("number", "title", "state", "draft", "html_url", "updated_at").forEach { (key, value) -> put(key, value) }
                item["head"].asObject()?.str("ref")?.let { put("head", it) }
                item["base"].asObject()?.str("ref")?.let { put("base", it) }
            }
        }
        return toolOk { put("pulls", JsonArray(pulls)) }
    }

    // ---- changes (always confirmed) ----

    private suspend fun writeFile(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val pathSegments = filePath(args.str("path")) ?: return toolFail(BAD_PATH)
        val content = args.str("content") ?: return toolFail("content is required (the complete new text of the file)")
        val message = args.str("message")?.trim().orEmpty()
        if (message.isEmpty()) return toolFail("message (the commit message) is required")
        val branch = branchArg(args) ?: return toolFail(BAD_BRANCH)

        val contentsSegments = repo.segments("contents") + pathSegments
        val existing = api.call("GET", contentsSegments, branch.query("ref"))
        val sha: String? = when {
            existing.code == 404 -> null
            !existing.ok -> return toolFail(errorText(existing))
            else -> existing.body.asObject()?.takeIf { it.str("type") == "file" }?.str("sha")
                ?: return toolFail("That path is a directory, not a file.")
        }
        val verb = if (sha == null) "Create" else "Update"
        val path = pathSegments.joinToString("/")
        val preview = content.take(PREVIEW_CHARS) +
            if (content.length > PREVIEW_CHARS) "\n…(${content.length} characters in total)" else ""
        val detail = "${repo.full} · ${branch.label()}\n$verb $path\nCommit message: $message\n\n$preview"
        if (!approved("GitHub: ${verb.lowercase()} file", detail)) return toolFail(DECLINED_MESSAGE)

        val body = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            if (sha != null) put("sha", sha)
            if (branch.name != null) put("branch", branch.name)
        }
        val result = requireOk(api.call("PUT", contentsSegments, body = body)).asObject()
        val commit = result?.get("commit").asObject()
        return toolOk {
            put("path", path)
            put("created", sha == null)
            commit?.str("sha")?.let { put("commit_sha", it) }
            commit?.str("html_url")?.let { put("commit_url", it) }
        }
    }

    private suspend fun deleteFile(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val pathSegments = filePath(args.str("path")) ?: return toolFail(BAD_PATH)
        val message = args.str("message")?.trim().orEmpty()
        if (message.isEmpty()) return toolFail("message (the commit message) is required")
        val branch = branchArg(args) ?: return toolFail(BAD_BRANCH)

        val contentsSegments = repo.segments("contents") + pathSegments
        val existing = api.call("GET", contentsSegments, branch.query("ref"))
        if (existing.code == 404) return toolFail("File not found.")
        val sha = requireOk(existing).asObject()?.takeIf { it.str("type") == "file" }?.str("sha")
            ?: return toolFail("That path is a directory, not a file.")
        val path = pathSegments.joinToString("/")
        if (!approved("GitHub: delete file", "${repo.full} · ${branch.label()}\nDelete $path\nCommit message: $message")) {
            return toolFail(DECLINED_MESSAGE)
        }
        val body = buildJsonObject {
            put("message", message)
            put("sha", sha)
            if (branch.name != null) put("branch", branch.name)
        }
        val commit = requireOk(api.call("DELETE", contentsSegments, body = body)).asObject()?.get("commit").asObject()
        return toolOk {
            put("deleted", path)
            commit?.str("html_url")?.let { put("commit_url", it) }
        }
    }

    private suspend fun createBranch(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val name = args.str("name")?.trim().orEmpty()
        if (!validRef(name)) return toolFail(BAD_BRANCH)
        val from = branchArg(args, "from") ?: return toolFail(BAD_BRANCH)
        val base = from.name ?: defaultBranch(repo)
        val ref = requireOk(api.call("GET", repo.segments("git", "ref", "heads") + base.split('/'))).asObject()
        val sha = ref?.get("object").asObject()?.str("sha") ?: return toolFail("Branch $base was not found.")
        if (!approved("GitHub: create branch", "${repo.full}\nCreate branch $name from $base")) return toolFail(DECLINED_MESSAGE)
        val body = buildJsonObject {
            put("ref", "refs/heads/$name")
            put("sha", sha)
        }
        requireOk(api.call("POST", repo.segments("git", "refs"), body = body))
        return toolOk {
            put("branch", name)
            put("from", base)
        }
    }

    private suspend fun createIssue(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val title = args.str("title")?.trim().orEmpty()
        if (title.isEmpty()) return toolFail("title is required")
        val text = args.str("body").orEmpty()
        val labels = args.str("labels").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val detail = "${repo.full}\n$title\n\n${text.take(PREVIEW_CHARS)}" +
            if (labels.isNotEmpty()) "\nLabels: ${labels.joinToString()}" else ""
        if (!approved("GitHub: create issue", detail)) return toolFail(DECLINED_MESSAGE)
        val body = buildJsonObject {
            put("title", title)
            if (text.isNotEmpty()) put("body", text)
            if (labels.isNotEmpty()) put("labels", JsonArray(labels.map(::JsonPrimitive)))
        }
        val created = requireOk(api.call("POST", repo.segments("issues"), body = body)).asObject()
        return toolOk { created?.pick("number", "html_url")?.forEach { (key, value) -> put(key, value) } }
    }

    private suspend fun comment(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val number = args.long("number")?.takeIf { it > 0 } ?: return toolFail("number is required")
        val text = args.str("body")?.trim().orEmpty()
        if (text.isEmpty()) return toolFail("body is required")
        if (!approved("GitHub: comment", "${repo.full} #$number\n\n${text.take(PREVIEW_CHARS)}")) return toolFail(DECLINED_MESSAGE)
        val created = requireOk(
            api.call("POST", repo.segments("issues", number.toString(), "comments"), body = buildJsonObject { put("body", text) }),
        ).asObject()
        return toolOk { created?.str("html_url")?.let { put("html_url", it) } }
    }

    private suspend fun createPullRequest(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val title = args.str("title")?.trim().orEmpty()
        if (title.isEmpty()) return toolFail("title is required")
        val head = args.str("head")?.trim().orEmpty()
        if (!validRef(head)) return toolFail("head must be the name of the branch with your changes")
        val baseArg = branchArg(args, "base") ?: return toolFail(BAD_BRANCH)
        val base = baseArg.name ?: defaultBranch(repo)
        val text = args.str("body").orEmpty()
        val draft = args.flag("draft") ?: false
        val detail = "${repo.full}\n$head → $base${if (draft) " (draft)" else ""}\n$title\n\n${text.take(PREVIEW_CHARS)}"
        if (!approved("GitHub: open pull request", detail)) return toolFail(DECLINED_MESSAGE)
        val body = buildJsonObject {
            put("title", title)
            put("head", head)
            put("base", base)
            if (text.isNotEmpty()) put("body", text)
            if (draft) put("draft", true)
        }
        val created = requireOk(api.call("POST", repo.segments("pulls"), body = body)).asObject()
        return toolOk { created?.pick("number", "html_url")?.forEach { (key, value) -> put(key, value) } }
    }

    private suspend fun mergePullRequest(args: JsonObject): String {
        val repo = parseRepo(args.str("repo")) ?: return toolFail(BAD_REPO)
        val number = args.long("number")?.takeIf { it > 0 } ?: return toolFail("number is required")
        val method = args.str("method")?.trim()?.lowercase()?.ifEmpty { null } ?: "squash"
        if (method !in MERGE_METHODS) return toolFail("method must be one of: merge, squash, rebase")
        if (!approved("GitHub: merge pull request", "${repo.full} #$number\nMerge method: $method")) return toolFail(DECLINED_MESSAGE)
        val merged = requireOk(
            api.call("PUT", repo.segments("pulls", number.toString(), "merge"), body = buildJsonObject { put("merge_method", method) }),
        ).asObject()
        return toolOk { merged?.pick("merged", "message", "sha")?.forEach { (key, value) -> put(key, value) } }
    }

    // ---- helpers ----

    private suspend fun approved(title: String, detail: String) = approver.confirm(title, detail, ApprovalLevel.SENSITIVE)

    private suspend fun defaultBranch(repo: Repo): String =
        requireOk(api.call("GET", repo.segments())).asObject()?.str("default_branch")
            ?: throw GitHubException("Could not read the default branch of ${repo.full}.")

    private fun requireOk(response: GitHubResponse): JsonElement? {
        if (!response.ok) throw GitHubException(errorText(response))
        return response.body
    }

    private fun errorText(response: GitHubResponse): String {
        val message = response.body.asObject()?.str("message") ?: "request failed"
        val hint = when (response.code) {
            401 -> " Check the GitHub token in the Agent screen (GitHub & Safety tab)."
            403 -> " (missing token permission, or rate limit)"
            404 -> " (not found, or the token cannot see it)"
            else -> ""
        }
        return "GitHub ${response.code}: $message.$hint"
    }

    private class Repo(val owner: String, val name: String) {
        val full: String get() = "$owner/$name"
        fun segments(vararg more: String): List<String> = listOf("repos", owner, name) + more
    }

    /** A branch/ref argument: [name] null means "not given" (use the default branch). */
    private class Ref(val name: String?) {
        fun query(key: String = "ref"): Map<String, String> = if (name == null) emptyMap() else mapOf(key to name)
        fun label(): String = name ?: "default branch"
    }

    private fun parseRepo(raw: String?): Repo? {
        val value = raw?.trim().orEmpty()
        if (!REPO.matches(value)) return null
        val parts = value.split('/')
        if (parts.any { it == "." || it == ".." }) return null
        return Repo(parts[0], parts[1])
    }

    /** Null = invalid; an empty list = the repository root. */
    private fun optionalPath(raw: String?): List<String>? {
        val path = raw?.trim()?.trim('/').orEmpty()
        return if (path.isEmpty()) emptyList() else splitPath(path)
    }

    private fun filePath(raw: String?): List<String>? {
        val path = raw?.trim()?.trim('/').orEmpty()
        return if (path.isEmpty()) null else splitPath(path)
    }

    private fun splitPath(path: String): List<String>? {
        val parts = path.split('/')
        return if (parts.any { it.isEmpty() || it == "." || it == ".." || it.contains('\\') }) null else parts
    }

    private fun validRef(name: String): Boolean =
        REF.matches(name) && !name.contains("..") && !name.contains("//") &&
            !name.startsWith("/") && !name.endsWith("/") && !name.endsWith(".lock")

    /** Optional branch argument; null = invalid value. */
    private fun branchArg(args: JsonObject, key: String = "branch"): Ref? {
        val value = args.str(key)?.trim()?.takeIf { it.isNotEmpty() }
        return if (value == null) Ref(null) else if (validRef(value)) Ref(value) else null
    }

    private fun refArg(args: JsonObject): Ref? = branchArg(args, "ref")

    private fun stateArg(args: JsonObject): String? {
        val state = args.str("state")?.trim()?.lowercase()?.ifEmpty { null } ?: "open"
        return state.takeIf { it in STATES }
    }

    private companion object {
        val REPO = Regex("^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$")
        val OWNER = Regex("^[A-Za-z0-9-]{1,39}$")
        val REF = Regex("^[A-Za-z0-9._/-]{1,200}$")
        val MERGE_METHODS = setOf("merge", "squash", "rebase")
        val STATES = setOf("open", "closed", "all")
        const val MAX_FILE_CHARS = 30_000
        const val BINARY_SCAN = 8_000
        const val PREVIEW_CHARS = 600
        const val BAD_REPO = "repo must look like owner/name"
        const val BAD_PATH = "path is missing or invalid (no empty, '.' or '..' parts)"
        const val BAD_BRANCH = "Invalid branch/ref name"
        const val BAD_STATE = "state must be open, closed or all"
    }
}
