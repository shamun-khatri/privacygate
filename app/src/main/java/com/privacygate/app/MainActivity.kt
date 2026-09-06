package com.privacygate.app

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import com.privacygate.app.ai.gemma.GemmaEnrichmentResult
import com.privacygate.app.ai.gemma.GemmaEnrichmentStatus
import com.privacygate.app.ai.gemma.GemmaModelAvailability
import com.privacygate.app.ai.gemma.GemmaProcessClient
import com.privacygate.app.ai.gemma.MlKitIndexSummary
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
import com.privacygate.app.settings.SensitivityCategory
import com.privacygate.app.studio.ui.MagicStudioScreen
import com.privacygate.app.ui.AppTabIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private lateinit var gemmaProcessClient: GemmaProcessClient
    private val visionIndexer = LocalVisionIndexer()

    private val photosState = mutableStateListOf<GalleryPhoto>()
    private val isIndexingState = mutableStateOf(false)
    private val indexedCountState = mutableIntStateOf(0)
    private val totalPhotoCountState = mutableIntStateOf(0)

    private var indexingJob: Job? = null
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyPreferences = PrivacyPreferences(this)
        mediaRepository = MediaRepository(this)
        photoIndexCache = PhotoIndexCache(this)
        gemmaProcessClient = GemmaProcessClient(this)

        handleShareIntent(intent)

        setContent {
            val state by PrivacyGateState.state.collectAsState()
            val prefsState by privacyPreferences.state.collectAsState()

            var selectedTab by remember { mutableStateOf(MainTab.HOME) }
            val photos = photosState
            val isIndexing by remember { isIndexingState }
            val indexedCount by remember { indexedCountState }
            val totalPhotoCount by remember { totalPhotoCountState }
            var activeStudioPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }

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
                                    plateWarningsEnabled = SensitivityCategory.VEHICLE_PLATE in prefsState.enabledCategories,
                                    onRefresh = { syncPhotos() },
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
                                    },
                                    onDeepAnalyze = { requestedPhoto ->
                                        val photoIndex = photos.indexOfFirst { it.id == requestedPhoto.id }
                                        if (photoIndex >= 0) {
                                            val currentPhoto = photos[photoIndex]
                                            when (gemmaProcessClient.modelAvailability()) {
                                                is GemmaModelAvailability.Ready -> {
                                                    photos[photoIndex] = currentPhoto.copy(
                                                        gemmaStatus = GemmaEnrichmentStatus.RUNNING
                                                    )
                                                    lifecycleScope.launch {
                                                        val input = currentPhoto.uri?.let {
                                                            prepareGemmaImage(it, currentPhoto.id)
                                                        }
                                                        val result = if (input == null) {
                                                            GemmaEnrichmentResult.Failed("Could not prepare photo")
                                                        } else {
                                                            try {
                                                                gemmaProcessClient.reEvaluate(
                                                                    image = input,
                                                                    summary = MlKitIndexSummary(
                                                                        labels = currentPhoto.labels,
                                                                        segments = currentPhoto.segments,
                                                                        documentType = currentPhoto.documentType,
                                                                        isSensitiveDocument = currentPhoto.isSensitiveDocument
                                                                    )
                                                                )
                                                            } finally {
                                                                input.delete()
                                                            }
                                                        }

                                                        val latestIndex = photos.indexOfFirst { it.id == currentPhoto.id }
                                                        if (latestIndex >= 0) {
                                                            val latest = photos[latestIndex]
                                                            photos[latestIndex] = when (result) {
                                                                is GemmaEnrichmentResult.Ready -> latest.copy(
                                                                    gemmaStatus = GemmaEnrichmentStatus.READY,
                                                                    gemmaEnrichment = result.enrichment,
                                                                    gemmaLatencyMs = result.latencyMs
                                                                )
                                                                is GemmaEnrichmentResult.Unavailable -> latest.copy(
                                                                    gemmaStatus = GemmaEnrichmentStatus.UNAVAILABLE
                                                                )
                                                                is GemmaEnrichmentResult.Failed -> latest.copy(
                                                                    gemmaStatus = GemmaEnrichmentStatus.FAILED
                                                                )
                                                            }
                                                            photoIndexCache.saveCache(photos.toList())
                                                        }
                                                    }
                                                }
                                                else -> photos[photoIndex] = currentPhoto.copy(
                                                    gemmaStatus = GemmaEnrichmentStatus.UNAVAILABLE
                                                )
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

    private suspend fun prepareGemmaImage(uri: Uri, photoId: Long): java.io.File? =
        withContext(Dispatchers.IO) {
            val bitmap = mediaRepository.loadDownscaledBitmap(uri, maxDimension = 896)
                ?: return@withContext null
            val directory = java.io.File(cacheDir, "gemma-inputs").apply { mkdirs() }
            val output = java.io.File(directory, "photo-$photoId.jpg")
            val saved = output.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            bitmap.recycle()
            output.takeIf { saved && it.isFile && it.length() > 0L }
        }

    fun syncPhotos() {
        lifecycleScope.launch {
            val loaded = mediaRepository.fetchGalleryPhotos()
            totalPhotoCountState.intValue = loaded.size

            val cached = photoIndexCache.loadCache()
            val existingById = photosState.associateBy { it.id }

            val merged = loaded.map { photo ->
                val existing = existingById[photo.id]
                if (existing != null && existing.isIndexed) {
                    existing
                } else {
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
                            extractedText = "",
                            isIndexed = true,
                            segments = meta.segments,
                            gemmaStatus = if (meta.gemmaEnrichment != null) GemmaEnrichmentStatus.READY else GemmaEnrichmentStatus.NOT_REQUESTED,
                            gemmaEnrichment = meta.gemmaEnrichment,
                            gemmaLatencyMs = meta.gemmaLatencyMs
                        )
                    } else {
                        photo
                    }
                }
            }

            if (photosState.size != merged.size || photosState.map { it.id } != merged.map { it.id }) {
                photosState.clear()
                photosState.addAll(merged)
            } else {
                for (i in merged.indices) {
                    if (photosState[i] != merged[i]) {
                        photosState[i] = merged[i]
                    }
                }
            }
            indexedCountState.intValue = photosState.count { it.isIndexed }

            val unindexed = photosState.filter { !it.isIndexed }
            android.util.Log.i("PrivacyGate", "syncPhotos: total=${photosState.size}, indexed=${indexedCountState.intValue}, unindexed=${unindexed.size}")

            if (unindexed.isNotEmpty()) {
                indexingJob?.cancel()
                indexingJob = lifecycleScope.launch(Dispatchers.Default) {
                    isIndexingState.value = true
                    try {
                        var processedSinceLastSave = 0
                        for (photo in unindexed) {
                            if (!isActive) break
                            android.util.Log.i("PrivacyGate", "Indexing photo id=${photo.id}, name=${photo.name}")
                            val photoUri = photo.uri
                            val bmp = if (photoUri != null) {
                                mediaRepository.loadDownscaledBitmap(photoUri, maxDimension = 512)
                            } else null

                            val indexed = if (bmp != null) {
                                try {
                                    visionIndexer.indexPhoto(photo, bmp)
                                } finally {
                                    bmp.recycle()
                                }
                            } else {
                                photo.copy(isIndexed = true)
                            }

                            withContext(Dispatchers.Main) {
                                val listIdx = photosState.indexOfFirst { it.id == photo.id }
                                if (listIdx != -1) {
                                    photosState[listIdx] = indexed
                                }
                                indexedCountState.intValue = photosState.count { it.isIndexed }
                            }
                            processedSinceLastSave++

                            if (processedSinceLastSave >= 5) {
                                photoIndexCache.saveCache(photosState.toList())
                                processedSinceLastSave = 0
                            }
                        }
                        photoIndexCache.saveCache(photosState.toList())
                        android.util.Log.i("PrivacyGate", "Finished indexing unindexed photos. Cache saved.")
                    } finally {
                        isIndexingState.value = false
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerMediaObserver()
    }

    override fun onResume() {
        super.onResume()
        syncPhotos()
    }

    override fun onStop() {
        super.onStop()
        unregisterMediaObserver()
    }

    private fun registerMediaObserver() {
        if (contentObserver != null) return
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                debounceJob?.cancel()
                debounceJob = lifecycleScope.launch {
                    delay(400)
                    syncPhotos()
                }
            }
        }
        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterMediaObserver() {
        contentObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            contentObserver = null
        }
        debounceJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterMediaObserver()
        indexingJob?.cancel()
        visionIndexer.close()
    }
}
