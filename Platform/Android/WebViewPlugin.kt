package com.dotnative.plugins

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URI

class InAppWebViewPlugin(private val activity: Activity) {

    private var dialog: Dialog? = null
    private var webView: WebView? = null
    private var title: TextView? = null

    init {

        val channel = NativeChannels.channel("dotnative.inapp-webview")
        channel.onDetach = {
            close()
        }
        channel.onReset = {
            close()
        }
        channel.handle("open") { args, reply ->
            open(args, reply)
        }
        channel.handle("goBack") { _, reply ->
            reply.success(
                webView?.let {
                    if (it.canGoBack()) {

                        it.goBack()
                        true
                    } else false
                } ?: false,
            )
        }
        channel.handle("goForward") { _, reply ->
            reply.success(
                webView?.let {
                    if (it.canGoForward()) {

                        it.goForward()
                        true
                    } else false
                } ?: false,
            )
        }
        channel.handle("reload") { _, reply ->
            webView?.reload()
            reply.success()
        }
        channel.handle("stop") { _, reply ->
            webView?.stopLoading()
            reply.success()
        }
        channel.handle("getCurrentUrl") { _, reply ->
            reply.success(webView?.url)
        }
        channel.handle("evaluateJavaScript") { args, reply ->
            evaluate(args, reply)
        }
        channel.handle("loadHtml") { args, reply ->
            loadHtml(args, reply)
        }
        channel.handle("setCookie") { args, reply ->
            setCookie(args, reply)
        }
        channel.handle("getCookies") { args, reply ->
            getCookies(args, reply)
        }
        channel.handle("clearCookies") { _, reply ->
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                reply.success()
            }
        }
        channel.handle("close") { _, reply ->
            close()
            reply.success()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun open(args: Any?, reply: PluginReply) {

        val fields = args as? Map<*, *>
        val url = fields?.get("url") as? String
        if (url == null || !validUrl(url)) {

            reply.failure("invalid_url", "An absolute HTTP or HTTPS URL is required")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {

            reply.failure("unavailable", "Activity is not available")
            return
        }
        close()
        try {

            val browser = WebView(activity)
            browser.settings.javaScriptEnabled = fields["javaScriptEnabled"] as? Boolean ?: false
            browser.settings.domStorageEnabled = true
            browser.settings.allowFileAccess = false
            browser.settings.allowContentAccess = false
            browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            (fields["userAgent"] as? String)
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    browser.settings.userAgentString = it
                }
            browser.webViewClient =
                object : WebViewClient() {

                    override fun onPageStarted(
                        view: WebView,
                        url: String,
                        favicon: android.graphics.Bitmap?,
                    ) {

                        title?.text =
                            view.title?.takeIf {
                                it.isNotBlank()
                            } ?: url
                    }

                    override fun onPageFinished(view: WebView, url: String) {

                        title?.text =
                            view.title?.takeIf {
                                it.isNotBlank()
                            } ?: url
                    }
                }

            val bar =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 4, 8, 4)
                }
            fun button(label: String, action: () -> Unit) =
                Button(activity).apply {
                    text = label
                    setOnClickListener {
                        action()
                    }
                    bar.addView(
                        this,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            button("‹") {
                if (browser.canGoBack()) browser.goBack()
            }
            button("›") {
                if (browser.canGoForward()) browser.goForward()
            }
            button("↻") {
                browser.reload()
            }
            val label =
                TextView(activity).apply {
                    text = url
                    maxLines = 1
                    setTextColor(Color.DKGRAY)
                }
            title = label
            bar.addView(
                label,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            button("Close") {
                close()
            }

            val layout =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.WHITE)
                    addView(
                        bar,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                    addView(
                        browser,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                    )
                }
            val window = Dialog(activity)
            window.requestWindowFeature(Window.FEATURE_NO_TITLE)
            window.setContentView(layout)
            window.setOnDismissListener {
                if (dialog === window) {

                    dialog = null
                    webView = null
                    title = null
                    browser.stopLoading()
                    browser.destroy()
                }
            }
            dialog = window
            webView = browser
            window.show()
            window.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            browser.loadUrl(url, headers(fields["headers"]))
            reply.success()
        } catch (error: Exception) {

            close()
            reply.failure("webview_unavailable", error.message ?: "Could not open the WebView")
        }
    }

    private fun evaluate(args: Any?, reply: PluginReply) {

        val script = (args as? Map<*, *>)?.get("script") as? String
        val browser = webView
        if (script == null || browser == null) {

            reply.failure("unavailable", "Open a WebView before evaluating JavaScript")
            return
        }
        browser.evaluateJavascript(script) { result ->
            reply.success(result)
        }
    }

    private fun loadHtml(args: Any?, reply: PluginReply) {

        val fields = args as? Map<*, *>
        val html = fields?.get("html") as? String
        val baseUrl = fields?.get("baseUrl") as? String
        if (html == null || baseUrl == null || !validUrl(baseUrl) || webView == null) {

            reply.failure(
                "unavailable",
                "Open a WebView and provide HTML with an HTTP or HTTPS base URL",
            )
            return
        }
        webView?.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        reply.success()
    }

    private fun setCookie(args: Any?, reply: PluginReply) {

        val fields = args as? Map<*, *>
        val url = fields?.get("url") as? String
        val name = fields?.get("name") as? String
        val value = fields?.get("value") as? String
        if (
            url == null ||
                name == null ||
                value == null ||
                !validUrl(url) ||
                !name.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
                value.contains('\r') ||
                value.contains('\n')
        ) {

            reply.failure("invalid_cookie", "Invalid cookie fields")
            return
        }
        val encoded = android.net.Uri.encode(value)
        val secure = if (url.startsWith("https://", ignoreCase = true)) "; Secure" else ""
        CookieManager.getInstance().setCookie(url, "$name=$encoded; Path=/; SameSite=Lax$secure") {
            accepted ->
            if (accepted) {

                CookieManager.getInstance().flush()
                reply.success()
            } else reply.failure("cookie_rejected", "The system WebView rejected the cookie")
        }
    }

    private fun getCookies(args: Any?, reply: PluginReply) {

        val url = (args as? Map<*, *>)?.get("url") as? String
        if (url == null || !validUrl(url)) {

            reply.failure("invalid_url", "An absolute HTTP or HTTPS URL is required")
            return
        }
        reply.success(CookieManager.getInstance().getCookie(url))
    }

    private fun close() {

        val current = dialog ?: return
        dialog = null
        val browser = webView
        webView = null
        title = null
        browser?.stopLoading()
        if (current.isShowing) current.dismiss()
        browser?.destroy()
    }

    private fun validUrl(value: String): Boolean =
        try {

            val uri = URI(value)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {

            false
        }

    private fun headers(value: Any?): Map<String, String> {

        val fields = value as? Map<*, *> ?: return emptyMap()
        require(
            fields.size <= 64 &&
                fields.all {
                    it.key is String && it.value is String
                },
        )
        return fields.entries.associate {
            it.key as String to it.value as String
        }
    }
}
