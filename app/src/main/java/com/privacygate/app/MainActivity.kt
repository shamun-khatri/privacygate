package com.privacygate.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.privacygate.app.ai.PrivacyInferenceEngine
import com.privacygate.app.gallery.ai.LocalVisionIndexer
import com.privacygate.app.gallery.data.MediaRepository
import com.privacygate.app.gallery.data.PhotoIndexCache
import com.privacygate.app.gallery.model.GalleryPhoto
import com.privacygate.app.gallery.ui.GalleryScreen
import com.privacygate.app.model.RiskLevel
import com.privacygate.app.protection.PrivacyGateState
import com.privacygate.app.redaction.RedactionEngine
import com.privacygate.app.sentinel.ui.SentinelScreen
import com.privacygate.app.settings.PrivacyPreferences
import com.privacygate.app.studio.ui.MagicStudioScreen
import com.privacygate.app.ui.AppTabIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String) {
    HOME("Protect"),
    GALLERY("Photos"),
    STUDIO("Studio")
}

class MainActivity : ComponentActivity() {

    private lateinit var privacyPreferences: PrivacyPreferences
    private lateinit var mediaRepository: MediaRepository
    private lateinit var photoIndexCache: PhotoIndexCache
    private val visionIndexer = LocalVisionIndexer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyPreferences = PrivacyPreferences(this)
        mediaRepository = MediaRepository(this)
        photoIndexCache = PhotoIndexCache(this)

        handleShareIntent(intent)

        setContent {
            val state by PrivacyGateState.state.collectAsState()
            val prefsState by privacyPreferences.state.collectAsState()

            var selectedTab by remember { mutableStateOf(MainTab.HOME) }
            val photos = remember { mutableStateListOf<GalleryPhoto>() }
            var isIndexing by remember { mutableStateOf(false) }
            var indexedCount by remember { mutableIntStateOf(0) }
            var totalPhotoCount by remember { mutableIntStateOf(0) }
            var activeStudioPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }

            // Load device photos & start background indexing
            LaunchedEffect(Unit) {
                val loaded = mediaRepository.fetchGalleryPhotos()
                totalPhotoCount = loaded.size

                val cached = photoIndexCache.loadCache()

                // Merge cached results instantly (< 5ms)
                val merged = loaded.map { photo ->
                    val meta = cached[photo.id]
                    if (meta != null) {
                        photo.copy(
                            labels = meta.labels,
                            faceCount = meta.faceCount,
                            hasPerson = meta.hasPerson,
                            isPortrait = meta.isPortrait,
                            isSelfie = meta.isSelfie,
                            isSensitiveDocument = meta.isSensitiveDocument,
                            documentType = meta.documentType,
                            extractedText = meta.extractedText,
                            isIndexed = true,
                            segments = meta.segments
                        )
                    } else {
                        photo
                    }
                }

                photos.clear()
                photos.addAll(merged)
                indexedCount = merged.count { it.isIndexed }

                // Index remaining unindexed photos progressively
                val unindexed = merged.filter { !it.isIndexed }
                if (unindexed.isNotEmpty()) {
                    isIndexing = true
                    withContext(Dispatchers.Default) {
                        var processedSinceLastSave = 0
                        for (photo in unindexed) {
                            val photoUri = photo.uri ?: continue
                            val bmp = mediaRepository.loadDownscaledBitmap(photoUri, maxDimension = 512)
                            if (bmp != null) {
                                val indexed = visionIndexer.indexPhoto(photo, bmp)
                                val listIdx = photos.indexOfFirst { it.id == photo.id }
                                if (listIdx != -1) {
                                    photos[listIdx] = indexed
                                }
                                indexedCount++
                                processedSinceLastSave++
                                bmp.recycle()

                                if (processedSinceLastSave >= 10) {
                                    photoIndexCache.saveCache(photos.toList())
                                    processedSinceLastSave = 0
                                }
                            }
                        }
                        photoIndexCache.saveCache(photos.toList())
                    }
                    isIndexing = false
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD7FC70),
                    secondary = Color(0xFF9CD7C0),
                    background = Color(0xFF090D0B),
                    surface = Color(0xFF121815),
                    surfaceVariant = Color(0xFF18201C),
                    onPrimary = Color(0xFF090D0B),
                    onSurface = Color(0xFFF3F6F2)
                )
            ) {
                Scaffold(
                    containerColor = Color(0xFF090D0B),
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF101512),
                            tonalElevation = 0.dp,
                            modifier = Modifier.height(78.dp)
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    icon = { AppTabIcon(tab, isSelected) },
                                    label = {
                                        Text(
                                            tab.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFFD8FF78) else Color(0xFF7E8B82)
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF10190D),
                                        selectedTextColor = Color(0xFFD8FF78),
                                        indicatorColor = Color(0xFF26331F),
                                        unselectedIconColor = Color(0xFF7E8B82),
                                        unselectedTextColor = Color(0xFF7E8B82)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            MainTab.HOME -> {
                                SentinelScreen(
                                    state = state,
                                    prefsState = prefsState,
                                    onToggleCategory = { privacyPreferences.toggleCategory(it) },
                                    onSetSensitivity = { privacyPreferences.setSensitivityLevel(it) },
                                    onAddKeyword = { privacyPreferences.addCustomKeyword(it) },
                                    onRemoveKeyword = { privacyPreferences.removeCustomKeyword(it) },
                                    onOpenSettings = {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    onOpenGallery = { selectedTab = MainTab.GALLERY }
                                )
                            }
                            MainTab.GALLERY -> {
                                GalleryScreen(
                                    photos = photos,
                                    isIndexing = isIndexing,
                                    indexedCount = indexedCount,
                                    totalCount = photos.size,
                                    onOpenInMagicStudio = { photo ->
                                        activeStudioPhoto = photo
                                        selectedTab = MainTab.STUDIO
                                    },
                                    onRedactAndShare = { photo ->
                                        val photoUri = photo.uri ?: return@GalleryScreen
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val bmp = mediaRepository.loadDownscaledBitmap(photoUri, 1200)
                                            if (bmp != null) {
                                                val engine = PrivacyInferenceEngine()
                                                val scan = engine.scan(bmp, prefsState)
                                                if (scan.regions.isNotEmpty()) {
                                                    RedactionEngine.saveAndShareRedacted(this@MainActivity, bmp, scan.regions)
                                                }
                                                bmp.recycle()
                                            }
                                        }
                                    }
                                )
                            }
                            MainTab.STUDIO -> {
                                MagicStudioScreen(
                                    initialPhoto = activeStudioPhoto,
                                    onBack = { selectedTab = MainTab.GALLERY }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri: Uri? = when {
                intent.data != null -> intent.data
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?: intent.getStringExtra(Intent.EXTRA_STREAM)?.let { Uri.parse(it) }
                }
                else -> {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                        ?: intent.getStringExtra(Intent.EXTRA_STREAM)?.let { Uri.parse(it) }
                }
            }
            if (uri != null) {
                processSharedUri(uri)
            }
        }
    }

    private fun processSharedUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream = if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    contentResolver.openInputStream(uri)
                }
                stream?.use { s ->
                    val bitmap = BitmapFactory.decodeStream(s)
                    if (bitmap != null) {
                        val engine = PrivacyInferenceEngine()
                        val result = engine.scan(bitmap, privacyPreferences.state.value)
                        android.util.Log.i("PrivacyGate", "Direct Preflight Scan complete: ${result.riskLevel}, type: ${result.documentType}, findings: ${result.findings.map { it.label }}")
                        PrivacyGateState.recordPreview("Direct Preflight Scan")
                        PrivacyGateState.recordScanResult(result)

                        // If user wants immediate one-tap redaction on shared file
                        if (result.riskLevel == RiskLevel.ACTIONABLE && result.regions.isNotEmpty()) {
                            RedactionEngine.saveAndShareRedacted(this@MainActivity, bitmap, result.regions)
                        }
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PrivacyGate", "Failed to process shared URI: $uri", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        visionIndexer.close()
    }
}
