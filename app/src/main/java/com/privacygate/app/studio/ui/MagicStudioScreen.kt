package com.privacygate.app.studio.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacygate.app.gallery.data.MediaRepository
import com.privacygate.app.gallery.model.GalleryPhoto
import com.privacygate.app.studio.MagicStudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StrokePoint(val offset: Offset, val radius: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicStudioScreen(
    initialPhoto: GalleryPhoto? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaRepo = remember { MediaRepository(context) }
    val studioEngine = remember { MagicStudioEngine() }

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var brushRadius by remember { mutableStateOf(30f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // User strokes on canvas
    val points = remember { mutableStateListOf<StrokePoint>() }

    // Pick image from device if initialPhoto is null
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val bmp = mediaRepo.loadDownscaledBitmap(it, maxDimension = 1080)
                currentBitmap = bmp
                originalBitmap = bmp?.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
                points.clear()
                isProcessing = false
            }
        }
    }

    // Load initial photo if provided
    LaunchedEffect(initialPhoto) {
        if (initialPhoto != null && currentBitmap == null) {
            val photoUri = initialPhoto.uri ?: return@LaunchedEffect
            isProcessing = true
            val bmp = mediaRepo.loadDownscaledBitmap(photoUri, maxDimension = 1080)
            currentBitmap = bmp
            originalBitmap = bmp?.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true)
            points.clear()
            isProcessing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🪄 Magic Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Select Photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (currentBitmap != null) {
                    if (originalBitmap != null && currentBitmap != originalBitmap) {
                        IconButton(onClick = {
                            currentBitmap = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                            points.clear()
                        }) {
                            Text("↺", fontSize = 18.sp)
                        }
                    }

                    IconButton(onClick = { points.clear() }) {
                        Text("🗑️", fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Canvas / Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E24)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = currentBitmap
            if (bmp != null) {
                val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(brushRadius) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    points.add(StrokePoint(offset, brushRadius))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    points.add(StrokePoint(change.position, brushRadius))
                                }
                            )
                        }
                ) {
                    // Draw the image scaled to fit
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val imgW = imageBitmap.width.toFloat()
                    val imgH = imageBitmap.height.toFloat()

                    val scale = minOf(canvasWidth / imgW, canvasHeight / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val left = (canvasWidth - drawW) / 2f
                    val top = (canvasHeight - drawH) / 2f

                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
                    )

                    // Draw user brush stroke overlays
                    for (p in points) {
                        drawCircle(
                            color = Color(0x99FF3B30),
                            radius = p.radius,
                            center = p.offset
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Photo Loaded",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { imagePicker.launch("image/*") }) {
                        Text("Pick a Photo to Erase Objects")
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Controls
        if (currentBitmap != null) {
            // Brush Size Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brush Size",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = brushRadius,
                    onValueChange = { brushRadius = it },
                    valueRange = 10f..80f,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val src = currentBitmap ?: return@Button
                        if (points.isEmpty()) return@Button

                        scope.launch {
                            isProcessing = true
                            // Create mask matching original bitmap dimensions
                            val maskBitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                            val canvas = AndroidCanvas(maskBitmap)
                            val paint = Paint().apply {
                                color = AndroidColor.WHITE
                                style = Paint.Style.FILL
                                isAntiAlias = true
                            }

                            val canvasW = canvasSize.width.toFloat()
                            val canvasH = canvasSize.height.toFloat()
                            if (canvasW > 0 && canvasH > 0) {
                                val imgW = src.width.toFloat()
                                val imgH = src.height.toFloat()
                                val scale = minOf(canvasW / imgW, canvasH / imgH)
                                val left = (canvasW - imgW * scale) / 2f
                                val top = (canvasH - imgH * scale) / 2f

                                for (p in points) {
                                    val srcX = (p.offset.x - left) / scale
                                    val srcY = (p.offset.y - top) / scale
                                    val srcR = p.radius / scale
                                    canvas.drawCircle(srcX, srcY, srcR, paint)
                                }
                            }

                            val inpainted = studioEngine.eraseObject(src, maskBitmap)
                            currentBitmap = inpainted
                            points.clear()
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = points.isNotEmpty() && !isProcessing
                ) {
                    Text("🪄 Magic Erase (${points.size})")
                }

                Button(
                    onClick = {
                        val bmp = currentBitmap ?: return@Button
                        scope.launch {
                            isProcessing = true
                            val uri = studioEngine.saveToCacheAndGetUri(context, bmp)
                            isProcessing = false

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Clean Photo"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = currentBitmap != null && !isProcessing
                ) {
                    Text("📤", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Share")
                }
            }
        }
    }
}
