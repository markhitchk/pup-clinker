package com.harleytg.puppyclicker

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PuppyLegalDocument(
    val title: String,
    val fileName: String,
    val rawUrl: String
) {
    TERMS(
        title = "Terms of Use",
        fileName = "terms-of-use.md",
        rawUrl = "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/legal/terms-of-use.md"
    ),
    PRIVACY(
        title = "Privacy Policy",
        fileName = "privacy-policy.md",
        rawUrl = "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/legal/privacy-policy.md"
    );

    val assetPath: String
        get() = "legal/$fileName"
}

private data class LegalLoadResult(
    val text: String,
    val source: String
)

/**
 * Repository-managed legal document loader.
 *
 * The canonical files live at repository root assets/legal/. The Android build copies those
 * exact files into the APK under the legal assets directory, so the app can always render the
 * repository copy internally without opening a browser or requiring GitHub credentials. If the
 * raw GitHub file is publicly reachable, a newer copy may be cached and rendered in the same
 * in-app viewer.
 */
private object StreamedLegalRepository {
    private const val CACHE_DIRECTORY = "legal"
    private const val REFRESH_MS = 6L * 60L * 60L * 1_000L
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 7_000
    private const val MAX_DOCUMENT_BYTES = 256 * 1024

    suspend fun load(context: Context, document: PuppyLegalDocument): LegalLoadResult =
        withContext(Dispatchers.IO) {
            val bundled = loadBundledRepositoryCopy(context, document)
            val cacheDir = File(context.filesDir, CACHE_DIRECTORY).apply { mkdirs() }
            val cache = File(cacheDir, document.fileName)
            val now = System.currentTimeMillis()

            if (cache.isFile && now - cache.lastModified() < REFRESH_MS) {
                val cached = runCatching { cache.readText(Charsets.UTF_8) }.getOrNull()
                if (!cached.isNullOrBlank()) {
                    return@withContext LegalLoadResult(cached, "repository update cache")
                }
            }

            // This succeeds only when the repository file is anonymously reachable. No GitHub
            // token is ever embedded in Puppy Clicker.
            val network = runCatching { download(document.rawUrl) }.getOrNull()
            if (!network.isNullOrBlank()) {
                runCatching { cache.writeText(network, Charsets.UTF_8) }
                return@withContext LegalLoadResult(network, "GitHub repository")
            }

            if (cache.isFile) {
                val cached = runCatching { cache.readText(Charsets.UTF_8) }.getOrNull()
                if (!cached.isNullOrBlank()) {
                    return@withContext LegalLoadResult(cached, "cached GitHub repository copy")
                }
            }

            if (!bundled.isNullOrBlank()) {
                return@withContext LegalLoadResult(bundled, "repository copy packaged with app")
            }

            LegalLoadResult(
                text = "This legal document is unavailable in this build.",
                source = "unavailable"
            )
        }

    private fun loadBundledRepositoryCopy(
        context: Context,
        document: PuppyLegalDocument
    ): String? = runCatching {
        context.assets.open(document.assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText().takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun download(rawUrl: String): String {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android")

            require(connection.responseCode in 200..299) {
                "GitHub returned HTTP ${connection.responseCode}"
            }
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(MAX_DOCUMENT_BYTES + 1)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                require(total <= MAX_DOCUMENT_BYTES) { "Legal document exceeded size limit" }
                buffer.copyOf(total)
            }
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}

private fun markdownToDisplayText(markdown: String): String =
    markdown.lineSequence()
        .map { line ->
            when {
                line.trim() == "---" -> ""
                line.startsWith("### ") -> line.removePrefix("### ")
                line.startsWith("## ") -> line.removePrefix("## ").uppercase()
                line.startsWith("# ") -> line.removePrefix("# ")
                line.startsWith("> ") -> line.removePrefix("> ")
                line.startsWith("- ") -> "• " + line.removePrefix("- ")
                else -> line
            }
        }
        .joinToString("\n")
        .replace("**", "")
        .replace("`", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

@Composable
internal fun PuppyLegalLinks(
    modifier: Modifier = Modifier,
    acknowledgementText: String? = null
) {
    var selected by remember { mutableStateOf<PuppyLegalDocument?>(null) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        acknowledgementText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { selected = PuppyLegalDocument.TERMS }) {
                Text("Terms of Use")
            }
            Text(
                "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { selected = PuppyLegalDocument.PRIVACY }) {
                Text("Privacy Policy")
            }
        }
    }

    selected?.let { document ->
        StreamedLegalDialog(document = document, onDismiss = { selected = null })
    }
}

@Composable
private fun StreamedLegalDialog(
    document: PuppyLegalDocument,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var body by remember(document) { mutableStateOf("Loading current document…") }
    var source by remember(document) { mutableStateOf("") }

    LaunchedEffect(document) {
        val result = StreamedLegalRepository.load(context, document)
        body = markdownToDisplayText(result.text)
        source = result.source
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(document.title, fontWeight = FontWeight.Black)
                if (source.isNotBlank()) {
                    Text(
                        "Source: $source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .heightIn(min = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(body, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.padding(bottom = 4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
