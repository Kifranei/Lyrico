package com.lonx.lyrico.plugin.i18n

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonx.lyrico.data.model.plugin.PluginI18n
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.plugin.runtime.QuickJsHostApi
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import org.junit.Assume.assumeTrue
import kotlinx.serialization.json.Json

@RunWith(AndroidJUnit4::class)
class PluginI18nRuntimeTest {
    @Test fun applePluginRunsOnNativeQuickJsInThreeLanguages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue("Check out Lyrico-Plugins/apple to run the Apple integration test", assets.list("")?.contains("manifest.json") == true)
        val context = instrumentation.targetContext
        val original = Configuration(context.resources.configuration)
        val root = File(context.cacheDir, "apple-i18n-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            fun copyAsset(assetPath: String, target: File) {
                val children = assets.list(assetPath).orEmpty()
                if (children.isEmpty()) assets.open(assetPath).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                else {
                    target.mkdirs()
                    children.forEach { copyAsset(if (assetPath.isEmpty()) it else "$assetPath/$it", File(target, it)) }
                }
            }
            copyAsset("", root)
            val manifest = Json { ignoreUnknownKeys = true }.decodeFromString<PluginManifest>(File(root, "manifest.json").readText())
            val strings = PluginStrings.load(root, manifest)
            val script = manifest.includeDirs.flatMap { dir -> File(root, dir).walkTopDown().filter { it.isFile && it.extension == "js" }.sortedBy { it.path }.toList() }
                .joinToString("\n;\n") { it.readText() } + "\n;\n" + File(root, manifest.entry).readText()
            PluginLocales.initialize(context)
            val runtime = QuickJsRuntime(hostApi = QuickJsHostApi(pluginStrings = strings))
            try {
                runtime.eval(script)
                // Exercise the actual Apple error and summary paths; only network access is replaced.
                runtime.eval("""
                    var capturedMessages = [];
                    Platform.log.warn = function(tag, message) { capturedMessages.push(message); };
                    Platform.log.debug = function(tag, message) { capturedMessages.push(message); };
                    getLyricsForSong = function() { throw new Error('offline fixture'); };
                    function checkApple(request) {
                      capturedMessages = [];
                      var result = getLyrics(request);
                      return { count: result.length, messages: capturedMessages };
                    }
                """.trimIndent())
                val request = """{"song":{"id":"123","title":"Apple Song","artist":"Artist","album":"Album","date":"2026"},"config":{}}"""
                for ((tag, expected) in listOf("en" to "Found 0 lyrics candidates for Apple Song.", "zh-CN" to "为 Apple Song 找到 0 个歌词候选。", "zh-TW" to "為 Apple Song 找到 0 個歌詞候選。")) {
                    PluginLocales.update(Configuration(original).apply { setLocales(LocaleList.forLanguageTags(tag)) })
                    val result = runtime.call("checkApple", request)
                    assertTrue(result, result.contains(expected))
                    assertTrue(result, result.contains("offline fixture"))
                }
            } finally { runtime.close() }
        } finally {
            PluginLocales.update(original)
            root.deleteRecursively()
        }
    }

    @Test fun nativeBridgeFormatsArgumentsAndRefreshesLanguageBetweenCalls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = Configuration(context.resources.configuration)
        val root = File(context.cacheDir, "i18n-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            PluginLocales.initialize(context)
            File(root, "en.json").writeText("""{"count":"Found %2${'$'}d candidates for %1${'$'}s."}""")
            File(root, "zh.json").writeText("""{"count":"为 %1${'$'}s 找到 %2${'$'}d 个候选。"}""")
            val manifest = PluginManifest("test.native", "Test", 1, "1", apiVersion = 4,
                i18n = PluginI18n("en", mapOf("en" to "en.json", "zh-Hans" to "zh.json")))
            val host = QuickJsHostApi(pluginStrings = PluginStrings.load(root, manifest))
            val runtime = QuickJsRuntime(hostApi = host)
            try {
                runtime.eval("""
                    function check(request) {
                      return Platform.i18n.getLocale() + ':' + Platform.i18n.t('count', request.title, request.count);
                    }
                    function bad(request) {
                      try { Platform.i18n.t('count', 'Apple', '2'); return 'accepted'; }
                      catch (e) { return 'rejected'; }
                    }
                """.trimIndent())
                PluginLocales.update(Configuration(original).apply { setLocales(LocaleList.forLanguageTags("en")) })
                assertEquals("\"en:Found 2 candidates for Apple.\"", runtime.call("check", """{"title":"Apple","count":2}"""))
                PluginLocales.update(Configuration(original).apply { setLocales(LocaleList.forLanguageTags("zh-CN")) })
                assertEquals("\"zh-Hans:为 Apple 找到 2 个候选。\"", runtime.call("check", """{"title":"Apple","count":2}"""))
                assertEquals("\"rejected\"", runtime.call("bad", "{}"))
            } finally { runtime.close() }
        } finally {
            PluginLocales.update(original)
            root.deleteRecursively()
        }
    }
}
