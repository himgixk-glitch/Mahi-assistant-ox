package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.gemini.MahiPersonality
import com.example.data.gemini.SupportedLanguage
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@Composable
fun SettingsDialog(
    selectedVoice: String,
    selectedLanguage: SupportedLanguage,
    sassLevel: Int,
    hasApiKey: Boolean,
    onVoiceChanged: (String) -> Unit,
    onLanguageChanged: (SupportedLanguage) -> Unit,
    onSassChanged: (Int) -> Unit,
    onTestVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val voices = listOf(
        Pair("Aoede", "Vibrant & expressive female"),
        Pair("Kore", "Warm & melodic female"),
        Pair("Puck", "Playful & energetic"),
        Pair("Fenrir", "Deep & confident")
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF0C0820),
            border = BorderStroke(1.5.dp, NeonCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MAHI CONFIGURATION",
                        color = NeonCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selection Section (भाषा चयन)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "LANGUAGE / भाषा",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Language Quick Select Cards Grid/Row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MahiPersonality.AVAILABLE_LANGUAGES.forEach { lang ->
                        val isSelected = lang.id == selectedLanguage.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF1E1742) else Color(0xFF120D2A))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onLanguageChanged(lang) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = lang.flag, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = lang.nativeLabel,
                                    color = if (isSelected) NeonCyan else TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Personality / Sass Level
                Text(
                    text = "SASS LEVEL",
                    color = NeonPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (sassLevel) {
                            1 -> "Sweet & Playful"
                            3 -> "Extra Sassy & Roasty 🔥"
                            else -> "Witty & Charming (Default)"
                        },
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Level $sassLevel / 3",
                        color = NeonAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = sassLevel.toFloat(),
                    onValueChange = { onSassChanged(it.toInt()) },
                    valueRange = 1f..3f,
                    steps = 1,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPink,
                        activeTrackColor = NeonPink,
                        inactiveTrackColor = Color(0xFF281C48)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("sass_level_slider")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Voice Model Selection
                Text(
                    text = "NEURAL VOICE",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voices.forEach { (voiceName, desc) ->
                        val isSelected = selectedVoice == voiceName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF1E1742) else Color(0xFF120D2A))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onVoiceChanged(voiceName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = voiceName,
                                    color = if (isSelected) NeonCyan else TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test Voice Button
                Button(
                    onClick = onTestVoice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("test_voice_sample_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TextWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TEST MAHI'S VOICE",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Status Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF130E26))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (hasApiKey) NeonGreen else NeonAmber)
                        )
                        Text(
                            text = if (hasApiKey)
                                "Gemini Live API & Grounding active"
                            else
                                "API Key placeholder mode (Secrets panel to set)",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
