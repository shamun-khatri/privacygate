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
import com.privacygate.app.gallery.model.GalleryPhoto
import com.privacygate.app.gallery.model.SmartCategory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryScreen(
    photos: List<GalleryPhoto>,
    isIndexing: Boolean,
    indexedCount: Int,
    totalCount: Int,
    onOpenInMagicStudio: (GalleryPhoto) -> Unit,
    onRedactAndShare: (GalleryPhoto) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SmartCategory.ALL) }
    var selectedPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }

    val filteredPhotos = remember(photos.size, indexedCount, searchQuery, selectedCategory) {
        photos.filter { photo ->
            photo.matchesCategory(selectedCategory) && photo.matchesQuery(searchQuery)
        }
    }

    val peopleCount = remember(photos.size, indexedCount) { photos.count { it.matchesCategory(SmartCategory.PEOPLE) } }
    val docCount = remember(photos.size, indexedCount) { photos.count { it.matchesCategory(SmartCategory.DOCUMENTS) } }
    val vehicleCount = remember(photos.size, indexedCount) { photos.count { it.matchesCategory(SmartCategory.VEHICLES) } }
    val foodCount = remember(photos.size, indexedCount) { photos.count { it.matchesCategory(SmartCategory.FOOD) } }
    val natureCount = remember(photos.size, indexedCount) { photos.count { it.matchesCategory(SmartCategory.NATURE) } }
    val screenshotCount = remember(photos.size, indexedCount) { photos.count { it.matchesCategory(SmartCategory.SCREENSHOTS) } }

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
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search 370+ photos (e.g. car, people, invoice)",
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
                        onClick = { selectedPhoto = photo }
                    )
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            onDismiss = { selectedPhoto = null },
            onOpenInMagicStudio = {
                selectedPhoto = null
                onOpenInMagicStudio(photo)
            },
            onRedactAndShare = {
                selectedPhoto = null
                onRedactAndShare(photo)
            }
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
            if (photo.isSensitiveDocument || photo.documentType != null) {
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
    onDismiss: () -> Unit,
    onOpenInMagicStudio: () -> Unit,
    onRedactAndShare: () -> Unit
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
                    }
                }

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
