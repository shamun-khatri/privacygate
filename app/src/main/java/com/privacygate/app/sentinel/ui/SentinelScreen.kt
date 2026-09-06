package com.privacygate.app.sentinel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacygate.app.model.RiskLevel
import com.privacygate.app.protection.DiagnosticsState
import com.privacygate.app.settings.PrivacyPreferencesState
import com.privacygate.app.settings.SensitivityCategory
import com.privacygate.app.settings.SensitivityLevel

private val Ink = Color(0xFF090D0B)
private val Panel = Color(0xFF121815)
private val Elevated = Color(0xFF18201C)
private val Lime = Color(0xFFD8FF78)
private val SoftLime = Color(0xFFA8C85D)
private val TextPrimary = Color(0xFFF3F6F2)
private val TextMuted = Color(0xFF8D9A91)
private val Hairline = Color(0xFF27322B)
private val Alert = Color(0xFFFF8C82)

@Composable
fun SentinelScreen(
    state: DiagnosticsState,
    prefsState: PrivacyPreferencesState,
    onToggleCategory: (SensitivityCategory) -> Unit,
    onSetSensitivity: (SensitivityLevel) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit
) {
    var showAllRules by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var keywordInput by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandHeader()
        ProtectionHero(state.isServiceConnected, onOpenSettings)
        QuickActions(onOpenGallery, onOpenSettings)
        ActivityStrip(state)
        state.lastScanResult?.let { RecentProtectionCard(it.riskLevel, it.documentType, it.findings.map { finding -> finding.label }, it.latencyMs) }
        ProtectionRules(
            prefsState = prefsState,
            expanded = showAllRules,
            onExpandedChange = { showAllRules = !showAllRules },
            onToggleCategory = onToggleCategory,
            onSetSensitivity = onSetSensitivity
        )
        AdvancedPrivacy(
            expanded = showAdvanced,
            onExpandedChange = { showAdvanced = !showAdvanced },
            keywordInput = keywordInput,
            onKeywordInput = { keywordInput = it },
            keywords = prefsState.customKeywords,
            onAdd = {
                if (keywordInput.isNotBlank()) {
                    onAddKeyword(keywordInput)
                    keywordInput = ""
                }
            },
            onRemove = onRemoveKeyword
        )
        Text(
            "Private by architecture. Every scan and mask stays on this device.",
            color = Color(0xFF667269), fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShieldMark(42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("PrivacyGate", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text("Personal privacy, always on", color = TextMuted, fontSize = 12.sp)
        }
        Surface(color = Elevated, shape = RoundedCornerShape(99.dp), border = BorderStroke(1.dp, Hairline)) {
            Text("EDGE AI", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun ShieldMark(markSize: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(markSize).clip(RoundedCornerShape(14.dp)).background(Lime), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(markSize * .54f)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h * .2f)
                lineTo(w * .88f, h * .68f)
                quadraticTo(w * .72f, h * .92f, w / 2f, h)
                quadraticTo(w * .28f, h * .92f, w * .12f, h * .68f)
                lineTo(0f, h * .2f)
                close()
            }
            drawPath(path, color = Ink, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Ink, Offset(w * .29f, h * .49f), Offset(w * .45f, h * .64f), 2.8.dp.toPx(), StrokeCap.Round)
            drawLine(Ink, Offset(w * .45f, h * .64f), Offset(w * .73f, h * .34f), 2.8.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun ProtectionHero(active: Boolean, onOpenSettings: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "status").animateFloat(
        .45f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF19241D), Color(0xFF101613))))
            .border(1.dp, Color(0xFF314136), RoundedCornerShape(28.dp)).padding(22.dp)
    ) {
        Canvas(Modifier.size(190.dp).align(Alignment.TopEnd).offset(x = 74.dp, y = (-86).dp)) {
            drawCircle(Lime.copy(alpha = .055f), radius = size.minDimension / 2)
            drawCircle(Lime.copy(alpha = .10f), radius = size.minDimension * .35f, style = Stroke(1.dp.toPx()))
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (active) Lime.copy(alpha = pulse) else Alert, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text(if (active) "PROTECTION ACTIVE" else "ACTION REQUIRED", color = if (active) Lime else Alert,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.25.sp)
            }
            Text(if (active) "Your shares are guarded." else "Turn on your privacy shield.", color = TextPrimary,
                fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.7).sp)
            Text(
                if (active) "Sensitive details are detected before they leave WhatsApp. Clean photos pass silently."
                else "Enable Accessibility once so PrivacyGate can protect the final send action.",
                color = Color(0xFFB8C2BB), fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier.widthIn(max = 310.dp)
            )
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = if (active) Elevated else Lime, contentColor = if (active) TextPrimary else Ink),
                shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) { Text(if (active) "Manage protection" else "Enable protection", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuickActions(onOpenGallery: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile("MASK", "Share safely", "Prepare a protected copy", Lime, onOpenGallery, Modifier.weight(1f))
        ActionTile("ACCESS", "System access", "Manage background shield", Color(0xFF9CD7C0), onOpenSettings, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(kicker: String, title: String, detail: String, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(kicker, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
    }
}

@Composable
private fun ActivityStrip(state: DiagnosticsState) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, Hairline, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Protection activity", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("THIS SESSION", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            CompactMetric(state.totalScans.toString(), "Checked", TextPrimary, Modifier.weight(1f))
            CompactMetric(state.actionableWarnings.toString(), "Protected", Alert, Modifier.weight(1f))
            CompactMetric(state.safeScans.toString(), "Passed", Lime, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactMetric(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun RecentProtectionCard(risk: RiskLevel, documentType: String?, findings: List<String>, latencyMs: Long) {
    val protected = risk == RiskLevel.ACTIONABLE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (protected) Color(0xFF211817) else Color(0xFF142018))
            .border(1.dp, if (protected) Color(0xFF49302D) else Color(0xFF294432), RoundedCornerShape(20.dp)).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(if (protected) Alert.copy(alpha = .12f) else Lime.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Text(if (protected) "!" else "✓", color = if (protected) Alert else Lime, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(if (protected) documentType ?: "Sensitive share protected" else "Clean share passed", color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (findings.isEmpty()) "No private fields found" else findings.distinct().take(2).joinToString(" · "), color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${latencyMs}ms", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ProtectionRules(
    prefsState: PrivacyPreferencesState,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onToggleCategory: (SensitivityCategory) -> Unit,
    onSetSensitivity: (SensitivityLevel) -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Hairline, RoundedCornerShape(24.dp)).padding(18.dp)) {
        Text("Protection rules", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Choose how cautious PrivacyGate should be.", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink).padding(4.dp)) {
            SensitivityLevel.entries.forEach { level ->
                val selected = prefsState.sensitivityLevel == level
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (selected) Lime else Color.Transparent)
                        .clickable { onSetSensitivity(level) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center
                ) {
                    Text(when (level) { SensitivityLevel.LOW -> "Low"; SensitivityLevel.BALANCED -> "Balanced"; SensitivityLevel.STRICT -> "Strict" },
                        color = if (selected) Ink else TextMuted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val visible = if (expanded) SensitivityCategory.entries else SensitivityCategory.entries.take(4)
        visible.forEach { category -> RuleRow(category, category in prefsState.enabledCategories) { onToggleCategory(category) } }
        TextButton(onClick = onExpandedChange, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (expanded) "Show fewer" else "View all ${SensitivityCategory.entries.size} rules", color = SoftLime)
        }
    }
}

@Composable
private fun RuleRow(category: SensitivityCategory, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).background(if (enabled) Lime.copy(alpha = .10f) else Elevated, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Text(category.displayName.take(1), color = if (enabled) Lime else TextMuted, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(category.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(category.description, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(
            checkedThumbColor = Ink, checkedTrackColor = Lime, uncheckedThumbColor = TextMuted, uncheckedTrackColor = Elevated,
            uncheckedBorderColor = Hairline
        ))
    }
}

@Composable
private fun AdvancedPrivacy(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    keywordInput: String,
    onKeywordInput: (String) -> Unit,
    keywords: Set<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Hairline, RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onExpandedChange).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Private keywords", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Protect names, addresses or project codes", color = TextMuted, fontSize = 11.sp)
            }
            Text(if (expanded) "−" else "+", color = Lime, fontSize = 24.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(keywordInput, onKeywordInput, Modifier.weight(1f), placeholder = { Text("Add a private term", fontSize = 12.sp) }, singleLine = true,
                        shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Lime, unfocusedBorderColor = Hairline))
                    Button(onClick = onAdd, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Add") }
                }
                keywords.forEach { keyword ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(keyword, color = TextPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(keyword) }) { Text("Remove", color = Alert, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}
