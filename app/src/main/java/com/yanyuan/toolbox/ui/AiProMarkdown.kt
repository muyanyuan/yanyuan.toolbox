package com.yanyuan.toolbox.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson

/**
 * 式句解码（新 AI 页专用）：Markdown + KaTeX 渲染（WebView）。
 *  - Markdown → HTML（marked，CDN）
 *  - 数学公式 → KaTeX 渲染（$...$ / $$...$$ / \(..\) / \[..\]，auto-render）
 * 高度随内容自适应（JS 回读 scrollHeight 换算 dp）。聊天本身需要联网，CDN 与接口同网。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathMarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var heightDp by remember { mutableStateOf(24) }

    // 首次加载模板；内容变化只增量 renderMarkdown，不重新 load。
    var loaded by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            loaded = true
                            renderAndMeasure(view, content, density) { heightDp = it }
                        }
                    }
                    loadDataWithBaseURL(null, katexMarkdownHtml(dark), "text/html", "utf-8", null)
                }
            },
            update = { w ->
                if (loaded) renderAndMeasure(w, content, density) { heightDp = it }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
        )
    }
}

private fun renderAndMeasure(
    view: WebView?,
    content: String,
    density: androidx.compose.ui.unit.Density,
    onHeightDp: (Int) -> Unit
) {
    view ?: return
    view.evaluateJavascript("renderMarkdown(" + Gson().toJson(content) + ")", null)
    view.evaluateJavascript("document.body.scrollHeight") { h ->
        val px = h?.trim('"')?.toFloatOrNull() ?: return@evaluateJavascript
        onHeightDp((px / density.density).toInt().coerceAtLeast(24))
    }
}

/** Markdown + KaTeX 模板（marked + katex auto-render，CDN）。 */
private fun katexMarkdownHtml(dark: Boolean): String {
    val textColor = if (dark) "#ECECEC" else "#1C1B1F"
    val codeBg = if (dark) "#2B2B31" else "#F1F0F4"
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
<style>
  html,body{margin:0;padding:0;background:transparent;color:$textColor;
    font-family:sans-serif;font-size:15px;line-height:1.55;word-break:break-word}
  p{margin:0 0 6px 0}
  pre{background:$codeBg;border-radius:10px;padding:10px;overflow-x:auto;margin:6px 0}
  code{background:$codeBg;border-radius:6px;padding:1px 4px;font-size:13px}
  table{border-collapse:collapse;margin:6px 0}
  th,td{border:1px solid #88884440;padding:4px 8px}
  blockquote{border-left:3px solid #88884460;margin:6px 0;padding:2px 10px}
  img{max-width:100%}
</style>
</head>
<body>
<div id="content"></div>
<script>
function renderMarkdown(text){
  document.getElementById('content').innerHTML = marked.parse(text || '');
  renderMathInElement(document.getElementById('content'), {
    delimiters: [
      {left:'$$', right:'$$', display:true},
      {left:'\\(', right:'\\)', display:false},
      {left:'\\[', right:'\\]', display:true},
      {left:'$', right:'$', display:false}
    ],
    throwOnError:false
  });
}
</script>
</body>
</html>
""".trimIndent()
}
