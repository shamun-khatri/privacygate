package com.privacygate.app.preflight

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privacygate.app.ai.PrivacyInferenceEngine
import com.privacygate.app.model.PrivacyScanResult
import com.privacygate.app.redaction.RedactionEngine
import com.privacygate.app.settings.PrivacyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreflightActivity : ComponentActivity() {
    private var sourceBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUri = extractSharedUri(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFD7FC70), background = Color(0xFF0D110F), surface = Color(0xFF171D19))) {
                var result by remember { mutableStateOf<PrivacyScanResult?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(sharedUri) {
                    if (sharedUri == null) { error = "No image was received"; return@LaunchedEffect }
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(sharedUri)?.use(BitmapFactory::decodeStream)
                        }
                        if (loaded == null) error = "This image could not be opened"
                        else {
                            sourceBitmap = loaded
                            val engine = PrivacyInferenceEngine()
                            result = try { engine.scan(loaded, PrivacyPreferences(this@PreflightActivity).state.value) }
                            finally { engine.close() }
                        }
                    } catch (_: Exception) { error = "This image could not be analyzed" }
                }
                PreflightScreen(
                    bitmap = sourceBitmap,
                    result = result,
                    error = error,
                    onCancel = { finish() },
                    onMaskAndSend = { labels ->
                        val bitmap = sourceBitmap
                        val scan = result
                        if (bitmap != null && scan != null) {
                            RedactionEngine.saveAndShareSmartMasked(this@PreflightActivity, bitmap, scan.regions, labels)
                        }
                    }
                )
            }
        }
    }

    private fun extractSharedUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
    }

    override fun onDestroy() { sourceBitmap?.recycle(); sourceBitmap = null; super.onDestroy() }
}

@Composable
private fun PreflightScreen(
    bitmap: Bitmap?,
    result: PrivacyScanResult?,
    error: String?,
    onCancel: () -> Unit,
    onMaskAndSend: (Set<String>) -> Unit
) {
    val accent = Color(0xFFD7FC70)
    Box(Modifier.fillMaxSize().background(Color(0xFF0D110F)).padding(20.dp)) {
        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Color.White); Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = onCancel) { Text("Close") }
            }
            bitmap == null || result == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = accent); Spacer(Modifier.height(14.dp)); Text("Scanning privately on this device…", color = Color.White)
            }
            else -> {
                val labels = remember(result) { result.regions.map { it.label }.distinct() }
                val selected = remember(result) { mutableStateListOf<String>().apply { addAll(labels) } }
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Privacy Preflight", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Close") }
                    }
                    Text(result.documentType ?: "Image review", color = accent, fontWeight = FontWeight.SemiBold)
                    Text("${result.latencyMs} ms • On-device • Zero cloud", color = Color(0xFF91A096), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    Image(bitmap.asImageBitmap(), "Shared image", Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                    Spacer(Modifier.height(14.dp))
                    Text("Choose fields to mask", color = Color.White, fontWeight = FontWeight.Bold)
                    labels.forEach { label ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (label in selected) selected.remove(label) else selected.add(label) }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(label in selected, onCheckedChange = { checked -> if (checked) { if (label !in selected) selected.add(label) } else selected.remove(label) })
                            Text(label, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text(if (label in selected) "MASK" else "REVEAL", color = if (label in selected) accent else Color(0xFFFFA0A0), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onMaskAndSend(selected.toSet()) }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = result.regions.isNotEmpty()) {
                        Text("✨ Apply masks & continue", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
