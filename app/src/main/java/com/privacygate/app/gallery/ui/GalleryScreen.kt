package com.privacygate.app.gallery.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.privacygate.app.ai.gemma.GemmaEnrichmentStatus
import com.privacygate.app.gallery.model.GalleryPhoto
import com.privacygate.app.gallery.model.SmartCategory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryScreen(
    photos: List<GalleryPhoto>,
    isIndexing: Boolean,
    indexedCount: Int,
    totalCount: Int,
    plateWarningsEnabled: Boolean,
    onRefresh: () -> Unit = {},
    onOpenInMagicStudio: (GalleryPhoto) -> Unit,
    onRedactAndShare: (GalleryPhoto) -> Unit,
    onDeepAnalyze: (GalleryPhoto) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SmartCategory.ALL) }
    var selectedPhotoId by remember { mutableStateOf<Long?>(null) }

    val filteredPhotos = photos.filter { photo ->
        photo.matchesCategory(selectedCategory) && photo.matchesQuery(searchQuery)
    }

    val peopleCount = photos.count { it.matchesCategory(SmartCategory.PEOPLE) }
    val docCount = photos.count { it.matchesCategory(SmartCategory.DOCUMENTS) }
    val vehicleCount = photos.count { it.matchesCategory(SmartCategory.VEHICLES) }
    val plateCount = photos.count { it.matchesCategory(SmartCategory.PLATES) }
    val foodCount = photos.count { it.matchesCategory(SmartCategory.FOOD) }
    val natureCount = photos.count { it.matchesCategory(SmartCategory.NATURE) }
    val screenshotCount = photos.count { it.matchesCategory(SmartCategory.SCREENSHOTS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D110F))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Prism Photos",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "100% On-Device • Zero Cloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF88A090),
                    fontSize = 11.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFF16221A),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26422E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFFD7FC70), CircleShape)
                        )
                        Text(
                            text = "Snapdragon 8 Elite",
                            color = Color(0xFFD7FC70),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isIndexing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFD7FC70)
                        )
                    } else {
                        Text("🔄", fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (photos.isNotEmpty()) "Search ${photos.size} photos (e.g. car, people, invoice)" else "Search photos (e.g. car, people, invoice)",
                    color = Color(0xFF6B7E72),
                    fontSize = 13.sp
                )
            },
            leadingIcon = { Text("🔍", fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Text("✕", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF151C17),
                unfocusedContainerColor = Color(0xFF151C17),
                focusedIndicatorColor = Color(0xFFD7FC70).copy(alpha = 0.6f),
                unfocusedIndicatorColor = Color(0xFF202B23),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isIndexing && totalCount > 0) {
            val progressPercent = if (totalCount > 0) (indexedCount * 100 / totalCount) else 0
            Surface(
                color = Color(0xFF1A261D),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C4533))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFD7FC70)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edge AI Auto-Segmenting",
                                color = Color(0xFFD7FC70),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$indexedCount / $totalCount ($progressPercent%)",
                            color = Color(0xFF88A090),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (totalCount > 0) indexedCount.toFloat() / totalCount else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFD7FC70),
                        trackColor = Color(0xFF233628),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SegmentPill(title = "All", icon = "🖼️", count = photos.size, isSelected = selectedCategory == SmartCategory.ALL, onClick = { selectedCategory = SmartCategory.ALL })
            SegmentPill(title = "People", icon = "👥", count = peopleCount, isSelected = selectedCategory == SmartCategory.PEOPLE, onClick = { selectedCategory = SmartCategory.PEOPLE })
            SegmentPill(title = "Docs & IDs", icon = "🛡️", count = docCount, isSelected = selectedCategory == SmartCategory.DOCUMENTS, onClick = { selectedCategory = SmartCategory.DOCUMENTS })
            SegmentPill(title = "Vehicles", icon = "🚗", count = vehicleCount, isSelected = selectedCategory == SmartCategory.VEHICLES, onClick = { selectedCategory = SmartCategory.VEHICLES })
            SegmentPill(title = "Number Plates", icon = "🚘", count = plateCount, isSelected = selectedCategory == SmartCategory.PLATES, onClick = { selectedCategory = SmartCategory.PLATES })
            SegmentPill(title = "Food", icon = "🍽️", count = foodCount, isSelected = selectedCategory == SmartCategory.FOOD, onClick = { selectedCategory = SmartCategory.FOOD })
            SegmentPill(title = "Nature", icon = "🌿", count = natureCount, isSelected = selectedCategory == SmartCategory.NATURE, onClick = { selectedCategory = SmartCategory.NATURE })
            SegmentPill(title = "Screenshots", icon = "📱", count = screenshotCount, isSelected = selectedCategory == SmartCategory.SCREENSHOTS, onClick = { selectedCategory = SmartCategory.SCREENSHOTS })
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (photos.isEmpty()) "Scanning device storage..." else "No photos match \"${if (searchQuery.isNotEmpty()) searchQuery else selectedCategory.label}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF88A090)
                    )
                    if (photos.isNotEmpty() && (searchQuery.isNotEmpty() || selectedCategory != SmartCategory.ALL)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            searchQuery = ""
                            selectedCategory = SmartCategory.ALL
                        }) {
                            Text("Reset filter to All (${photos.size})", color = Color(0xFFD7FC70))
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 74.dp)
            ) {
                items(filteredPhotos, key = { it.id }) { photo ->
                    PhotoThumbnailCard(
                        photo = photo,
                        showPlateWarning = plateWarningsEnabled,
                        onClick = { selectedPhotoId = photo.id }
                    )
                }
            }
        }
    }

    selectedPhotoId?.let { id ->
        val photo = photos.firstOrNull { it.id == id } ?: return@let
        PhotoDetailDialog(
            photo = photo,
            plateWarningsEnabled = plateWarningsEnabled,
            onDismiss = { selectedPhotoId = null },
            onOpenInMagicStudio = {
                selectedPhotoId = null
                onOpenInMagicStudio(photo)
            },
            onRedactAndShare = {
                selectedPhotoId = null
                onRedactAndShare(photo)
            },
            onDeepAnalyze = { onDeepAnalyze(photo) }
        )
    }
}

@Composable
fun SegmentPill(
    title: String,
    icon: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFFD7FC70) else Color(0xFF18201B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFD7FC70) else Color(0xFF26332A)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, fontSize = 12.sp)
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF10190D) else Color(0xFFD0DDD4)
            )
            if (count > 0) {
                Surface(
                    color = if (isSelected) Color(0xFF10190D).copy(alpha = 0.15f) else Color(0xFF233027),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF10190D) else Color(0xFF88A090),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoThumbnailCard(
    photo: GalleryPhoto,
    showPlateWarning: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF151D18))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photo.uri)
                .crossfade(true)
                .size(320, 320)
                .build(),
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            if (showPlateWarning && photo.gemmaEnrichment?.registrationPlateVisible == true) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = Color(0xFFE16A2B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "🚘 PLATE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            } else if (photo.isSensitiveDocument || photo.documentType != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (photo.documentType != null) "🛡️ ${photo.documentType}" else "🛡️ DOC",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            } else if (photo.faceCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = Color(0xFF1976D2),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "👤 ${photo.faceCount}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            if (photo.bucketName != null && photo.bucketName.isNotBlank() && photo.bucketName != "0") {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = photo.bucketName,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCFDBD2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoDetailDialog(
    photo: GalleryPhoto,
    plateWarningsEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenInMagicStudio: () -> Unit,
    onRedactAndShare: () -> Unit,
    onDeepAnalyze: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A16)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28362D)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photo.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = Color(0xFF1B241E),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3A30))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "On-Device Segments",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD7FC70)
                            )
                            if (photo.faceCount > 0) {
                                Text(
                                    text = "👤 ${photo.faceCount} ${if (photo.faceCount == 1) "Person" else "People"}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF81D4FA),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (seg in photo.segments) {
                                Surface(
                                    color = Color(0xFF243329),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = seg,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD7FC70),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            for (label in photo.labels.take(6)) {
                                Surface(
                                    color = Color(0xFF1E2822),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        color = Color(0xFFBDCCC2),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (photo.isSensitiveDocument) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF3B1E1E),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ Sensitive Document: ${photo.documentType ?: "Private Records"}",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }

                        if (plateWarningsEnabled && photo.gemmaEnrichment?.registrationPlateVisible == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF3A2818),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🚘 Visible vehicle number plate — review before sharing",
                                    color = Color(0xFFFFB37B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(7.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                GemmaLayerCard(photo = photo, onDeepAnalyze = onDeepAnalyze)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenInMagicStudio,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7FC70)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🪄 Magic Erase", color = Color(0xFF121A0E), fontWeight = FontWeight.Bold)
                    }

                    if (photo.isSensitiveDocument) {
                        Button(
                            onClick = onRedactAndShare,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🛡️ Redact", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GemmaLayerCard(photo: GalleryPhoto, onDeepAnalyze: () -> Unit) {
    val enrichment = photo.gemmaEnrichment
    Surface(
        color = Color(0xFF171F1A),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3A30))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Second-layer re-evaluation", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Gemma 4 E2B • on device", color = Color(0xFF8FA398), fontSize = 10.sp)
                }
                when (photo.gemmaStatus) {
                    GemmaEnrichmentStatus.RUNNING, GemmaEnrichmentStatus.QUEUED ->
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFD7FC70))
                    GemmaEnrichmentStatus.READY -> Text("✓ READY", color = Color(0xFFD7FC70), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    else -> Unit
                }
            }

            if (photo.gemmaStatus == GemmaEnrichmentStatus.READY && enrichment != null) {
                if (enrichment.caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(enrichment.caption, color = Color(0xFFDCE7DF), fontSize = 12.sp)
                }
                val dynamicLabels = (enrichment.addedLabels + enrichment.confirmedMlKitLabels).distinct().take(10)
                if (dynamicLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        dynamicLabels.forEach { label ->
                            Surface(color = Color(0xFF243329), shape = RoundedCornerShape(6.dp)) {
                                Text(label, color = Color(0xFFCCEDB6), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
                if (enrichment.conflictingLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Re-evaluated ML Kit: questioned ${enrichment.conflictingLabels.joinToString()}",
                        color = Color(0xFFFFB37B),
                        fontSize = 10.sp
                    )
                }
                photo.gemmaLatencyMs?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Analyzed in ${it / 1000f}s", color = Color(0xFF75877D), fontSize = 9.sp)
                }
            } else if (photo.gemmaStatus == GemmaEnrichmentStatus.UNAVAILABLE || photo.gemmaStatus == GemmaEnrichmentStatus.FAILED) {
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    if (photo.gemmaStatus == GemmaEnrichmentStatus.UNAVAILABLE) "Gemma model is not installed yet. Fast ML Kit indexing remains active."
                    else "Deep analysis failed. Fast ML Kit indexing remains active.",
                    color = Color(0xFFB8C4BC),
                    fontSize = 10.sp
                )
            }

            if (photo.gemmaStatus != GemmaEnrichmentStatus.RUNNING && photo.gemmaStatus != GemmaEnrichmentStatus.QUEUED) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDeepAnalyze,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD7FC70))
                ) {
                    Text(if (photo.gemmaStatus == GemmaEnrichmentStatus.READY) "Re-analyze photo" else "✨ Deep analyze with Gemma", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
