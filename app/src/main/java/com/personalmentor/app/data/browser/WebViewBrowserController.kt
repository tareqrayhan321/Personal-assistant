package com.personalmentor.app.data.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.personalmentor.app.domain.browser.BrowserController
import com.personalmentor.app.domain.browser.BrowserException
import com.personalmentor.app.domain.browser.BrowserPage
import com.personalmentor.app.domain.browser.PageElement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the Browser tab shows (address bar, progress, back/forward buttons). */
data class BrowserUiState(
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/**
 * One app-wide WebView that both the agent and the user drive. The agent reads pages through injected
 * JavaScript (text + numbered interactive elements) and acts by element id; there is no JavaScript bridge into
 * the app. The same WebView is shown in the Agent screen's Browser tab so the user can watch, log in, or take over.
 *
 * Only http(s) is allowed; other schemes (intent:, file:, javascript:, market:…) are blocked. The WebView is
 * created lazily on the main thread and keeps working (laid out off-screen) while the Browser tab is closed.
 */
@Singleton
class WebViewBrowserController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val json: Json,
) : BrowserController {

    private val contextWrapper = MutableContextWrapper(appContext)
    private var webView: WebView? = null

    private val _ui = MutableStateFlow(BrowserUiState())
    val ui: StateFlow<BrowserUiState> = _ui.asStateFlow()

    @Volatile private var lastElements: Map<Int, PageElement> = emptyMap()
    @Volatile private var lastUrl: String? = null
    @Volatile private var lastError: String? = null

    // ---- used by the Browser tab (main thread) ----

    /** Returns the shared WebView for display, swapping its context to the visible activity. */
    fun attach(activityContext: Context): WebView {
        contextWrapper.setBaseContext(activityContext)
        val view = view()
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    /** Hides the WebView from the screen but keeps it alive and laid out so the agent can keep using it. */
    fun detach() {
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.layoutOffscreen()
        }
        contextWrapper.setBaseContext(appContext)
    }

    fun navigate(input: String) {
        val target = normalize(input) ?: searchUrl(input)
        _ui.update { it.copy(loading = true) }
        view().loadUrl(target)
    }

    fun goBack() { webView?.takeIf { it.canGoBack() }?.goBack() }
    fun goForward() { webView?.takeIf { it.canGoForward() }?.goForward() }
    fun reload() { webView?.reload() }

    /** Cookies, local storage, cache and history (logs the user out of every site). */
    fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView?.apply {
            clearCache(true)
            clearHistory()
        }
        lastElements = emptyMap()
    }

    // ---- BrowserController (called by the agent, any thread) ----

    override suspend fun open(url: String): BrowserPage {
        val target = normalize(url) ?: throw BrowserException("Only http(s) URLs can be opened.")
        onMain {
            _ui.update { it.copy(loading = true) }
            view().loadUrl(target)
        }
        awaitIdle(START_DELAY_MS)
        return snapshot()
    }

    override suspend fun read(): BrowserPage {
        if (onMain { view().url } == null) throw BrowserException("No page is open yet. Call browser_open first.")
        return snapshot()
    }

    override suspend fun click(id: Int): BrowserPage {
        runAction(clickScript(id), id)
        return snapshot()
    }

    override suspend fun type(id: Int, text: String, submit: Boolean): BrowserPage {
        runAction(typeScript(id, text, submit), id)
        return snapshot()
    }

    override suspend fun select(id: Int, option: String): BrowserPage {
        runAction(selectScript(id, option), id)
        return snapshot()
    }

    override suspend fun scroll(direction: String): BrowserPage {
        val script = when (direction) {
            "up" -> "window.scrollBy(0, -Math.round(window.innerHeight * 0.8)); 'ok'"
            "down" -> "window.scrollBy(0, Math.round(window.innerHeight * 0.8)); 'ok'"
            "top" -> "window.scrollTo(0, 0); 'ok'"
            "bottom" -> "window.scrollTo(0, document.documentElement.scrollHeight); 'ok'"
            else -> throw BrowserException("direction must be up, down, top or bottom.")
        }
        if (unwrap(evalJs(script)) != "ok") throw BrowserException("Could not scroll.")
        delay(SCROLL_DELAY_MS)
        return snapshot()
    }

    override suspend fun back(): BrowserPage {
        val moved = onMain {
            val view = view()
            if (view.canGoBack()) {
                _ui.update { it.copy(loading = true) }
                view.goBack()
                true
            } else {
                false
            }
        }
        if (!moved) throw BrowserException("There is no previous page.")
        awaitIdle(START_DELAY_MS)
        return snapshot()
    }

    override fun elementInfo(id: Int): PageElement? = lastElements[id]

    override fun currentUrl(): String? = lastUrl

    // ---- internals ----

    private suspend fun runAction(script: String, id: Int) {
        val result = unwrap(evalJs(script))
        when (result) {
            "ok" -> Unit
            "not_found" -> throw BrowserException("Element $id was not found. Call browser_read for fresh ids.")
            "not_editable" -> throw BrowserException("Element $id cannot be typed into.")
            "no_option" -> throw BrowserException("That option does not exist in element $id.")
            else -> throw BrowserException("The page rejected the action ($result).")
        }
        awaitIdle(ACTION_DELAY_MS)
    }

    private suspend fun snapshot(): BrowserPage {
        val raw = unwrap(evalJs(SNAPSHOT_SCRIPT))
        if (raw.isBlank()) throw BrowserException("The page returned no content.")
        val page = json.decodeFromString(BrowserPage.serializer(), raw).copy(error = lastError)
        lastElements = page.elements.associateBy { it.id }
        lastUrl = page.url
        return page
    }

    /** Waits for an action to start navigating, for loading to finish, and for the page to settle. */
    private suspend fun awaitIdle(firstDelayMs: Long) {
        delay(firstDelayMs)
        withTimeoutOrNull(LOAD_TIMEOUT_MS) { _ui.first { !it.loading } }
        delay(SETTLE_MS)
    }

    private suspend fun evalJs(script: String): String {
        val result = withTimeoutOrNull(JS_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String> { cont ->
                    view().evaluateJavascript(script) { raw -> if (cont.isActive) cont.resume(raw ?: "null") }
                }
            }
        }
        return result ?: throw BrowserException("The page did not respond.")
    }

    /** evaluateJavascript returns JSON; our scripts return a string, which arrives JSON-quoted. */
    private fun unwrap(raw: String): String =
        if (raw == "null" || raw.isBlank()) "" else json.parseToJsonElement(raw).jsonPrimitive.content

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private fun view(): WebView = webView ?: createWebView().also { webView = it }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(contextWrapper).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase()
                return scheme != "http" && scheme != "https" // true = cancel (intent:, market:, file:, javascript:…)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                lastError = null
                webView?.publishState(loading = true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                webView?.publishState(loading = false)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) lastError = "${error?.description} (code ${error?.errorCode})"
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _ui.update { it.copy(progress = newProgress) }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                _ui.update { it.copy(title = title.orEmpty()) }
            }

            // The default dialogs need an activity window; answer them here (confirm/prompt = cancel, the safe choice).
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?,
            ): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
        }
        layoutOffscreen()
    }

    private fun WebView.publishState(loading: Boolean) {
        _ui.update {
            it.copy(
                url = url.orEmpty(),
                title = title.orEmpty(),
                loading = loading,
                canGoBack = canGoBack(),
                canGoForward = canGoForward(),
            )
        }
    }

    /** A WebView that was never attached has size 0 and reports every element as invisible; give it a screen-sized layout. */
    private fun WebView.layoutOffscreen() {
        val metrics = appContext.resources.displayMetrics
        measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if (text.contains("://")) text else "https://$text"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return url.toString()
    }

    private fun searchUrl(query: String): String =
        "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8")

    private companion object {
        const val START_DELAY_MS = 300L
        const val ACTION_DELAY_MS = 800L
        const val SCROLL_DELAY_MS = 400L
        const val SETTLE_MS = 500L
        const val LOAD_TIMEOUT_MS = 25_000L
        const val JS_TIMEOUT_MS = 10_000L

        /** JavaScript string literal for [value] (a JSON string is a valid JS string). */
        fun js(value: String): String = JsonPrimitive(value).toString()

        fun clickScript(id: Int) = """
            (function(){
              var e=document.querySelector('[data-agent-id="$id"]');
              if(!e) return 'not_found';
              e.scrollIntoView({block:'center'});
              e.click();
              return 'ok';
            })()
        """.trimIndent()

        fun typeScript(id: Int, text: String, submit: Boolean) = """
            (function(){
              var e=document.querySelector('[data-agent-id="$id"]');
              if(!e) return 'not_found';
              var text=${js(text)};
              e.scrollIntoView({block:'center'});
              e.focus();
              if(e.isContentEditable){
                e.textContent=text;
              } else if(e.tagName==='INPUT'||e.tagName==='TEXTAREA'){
                var proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
                var d=Object.getOwnPropertyDescriptor(proto,'value');
                if(d&&d.set){d.set.call(e,text);}else{e.value=text;}
              } else { return 'not_editable'; }
              e.dispatchEvent(new Event('input',{bubbles:true}));
              e.dispatchEvent(new Event('change',{bubbles:true}));
              if($submit){
                var f=e.form;
                if(f){ if(f.requestSubmit){f.requestSubmit();}else{f.submit();} }
                else {
                  ['keydown','keypress','keyup'].forEach(function(t){
                    e.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
                  });
                }
              }
              return 'ok';
            })()
        """.trimIndent()

        fun selectScript(id: Int, option: String) = """
            (function(){
              var e=document.querySelector('[data-agent-id="$id"]');
              if(!e) return 'not_found';
              if(e.tagName!=='SELECT') return 'not_editable';
              var wanted=${js(option)}.toLowerCase();
              var hit=null;
              for(var i=0;i<e.options.length;i++){
                var o=e.options[i];
                if(o.value.toLowerCase()===wanted||o.text.trim().toLowerCase()===wanted){hit=o;break;}
              }
              if(!hit) return 'no_option';
              e.value=hit.value;
              e.dispatchEvent(new Event('input',{bubbles:true}));
              e.dispatchEvent(new Event('change',{bubbles:true}));
              return 'ok';
            })()
        """.trimIndent()

        /** Numbers the interactive elements near the viewport and returns url, title, visible text and elements as JSON. */
        val SNAPSHOT_SCRIPT = """
            (function(){
              var MAX_EL=60, MAX_TEXT=6000;
              var vh=window.innerHeight||800;
              document.querySelectorAll('[data-agent-id]').forEach(function(x){x.removeAttribute('data-agent-id');});
              var sel='a[href],button,input,textarea,select,[role=button],[role=link],[role=checkbox],[role=tab],[role=menuitem],[onclick],[contenteditable=true]';
              var nodes=document.querySelectorAll(sel), els=[], n=0;
              for(var i=0;i<nodes.length&&n<MAX_EL;i++){
                var e=nodes[i];
                if(e.type==='hidden') continue;
                var r=e.getBoundingClientRect();
                if(r.width<=0||r.height<=0) continue;
                var cs=getComputedStyle(e);
                if(cs.visibility==='hidden'||cs.display==='none') continue;
                if(r.bottom<-0.5*vh||r.top>1.5*vh) continue;
                n++;
                e.setAttribute('data-agent-id',String(n));
                var tag=e.tagName.toLowerCase();
                var isField=(tag==='input'||tag==='textarea');
                var ac=(e.getAttribute('autocomplete')||'').toLowerCase();
                var secret=(e.type==='password')||/^(cc-|one-time-code|current-password|new-password)/.test(ac);
                var label=(e.getAttribute('aria-label')||e.innerText||e.placeholder||e.title||e.name||e.value||'').replace(/\s+/g,' ').trim().slice(0,80);
                var item={id:n,tag:tag,text:label};
                if(e.type&&tag!=='a') item.type=e.type;
                if(tag==='a') item.href=e.href;
                if(isField&&!secret&&e.type!=='checkbox'&&e.type!=='radio'&&e.value) item.value=String(e.value).slice(0,60);
                if(e.type==='checkbox'||e.type==='radio') item.checked=!!e.checked;
                if(secret) item.secret=true;
                els.push(item);
              }
              var text=(document.body?document.body.innerText:'').replace(/\n{3,}/g,'\n\n').slice(0,MAX_TEXT);
              return JSON.stringify({url:location.href,title:document.title,text:text,elements:els,scrollY:Math.round(window.scrollY),pageHeight:document.documentElement.scrollHeight});
            })()
        """.trimIndent()
    }
}
