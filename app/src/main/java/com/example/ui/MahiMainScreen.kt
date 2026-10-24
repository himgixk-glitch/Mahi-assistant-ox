package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.CentralMicTrigger
import com.example.ui.components.CyberHeader
import com.example.ui.components.CyberWaveform
import com.example.ui.components.FuturisticOrbVisualizer
import com.example.ui.components.LanguageSelectDialog
import com.example.ui.components.SassyTranscriptDrawer
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.CyberDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@Composable
fun MahiMainScreen(
    viewModel: MahiViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startSession()
        }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Active amplitude dynamically picks input when listening or output when speaking
    val activeAmplitude = when (uiState.sessionState) {
        is SessionState.Speaking -> uiState.outputAmplitude
        is SessionState.Listening -> uiState.inputAmplitude
        else -> 0f
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize(),
        containerColor = CyberDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0C071E),
                            CyberDark,
                            Color(0xFF06040F)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Futuristic Top Bar
                CyberHeader(
                    sessionState = uiState.sessionState,
                    hasApiKey = uiState.hasApiKey,
                    selectedLanguage = uiState.selectedLanguage,
                    onOpenLanguageSelect = { showLanguageDialog = true },
                    onOpenSettings = { showSettingsDialog = true }
                )

                // 2. Central AI Hologram Orb Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FuturisticOrbVisualizer(
                        sessionState = uiState.sessionState,
                        amplitude = activeAmplitude
                    )

                    // Central Avatar Logo or Hologram Crest
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(2.dp, NeonCyan.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_mahi_icon_1792828807472),
                            contentDescription = "Mahi Hologram",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 3. Dynamic Soundwave Display
                CyberWaveform(
                    sessionState = uiState.sessionState,
                    amplitude = activeAmplitude,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Central Power / Mic Button
                CentralMicTrigger(
                    sessionState = uiState.sessionState,
                    amplitude = activeAmplitude,
                    onClick = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleSession(hasPermission = true)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Transcripts, Sassy Quote Cards, Tool Alerts & Quick Banter
                SassyTranscriptDrawer(
                    sessionState = uiState.sessionState,
                    transcripts = uiState.transcripts,
                    currentSpeechText = uiState.currentSpeechText,
                    activeToolBadge = uiState.activeToolBadge,
                    selectedLanguage = uiState.selectedLanguage,
                    onQuickPromptSelected = { prompt ->
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.sendQuickPrompt(prompt)
                        }
                    },
                    onInterruptSpeech = {
                        viewModel.interruptCurrentSpeech()
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Error or Permission Warning Banner
            if (!hasMicPermission) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3B0014))
                        .border(1.dp, NeonRed, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mic permission needed to hear you",
                                color = TextWhite,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_mic_permission_button")
                        ) {
                            Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Language Selection Modal
    if (showLanguageDialog) {
        LanguageSelectDialog(
            selectedLanguage = uiState.selectedLanguage,
            onLanguageSelected = { viewModel.updateLanguage(it) },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Settings Modal
    if (showSettingsDialog) {
        SettingsDialog(
            selectedVoice = uiState.selectedVoice,
            selectedLanguage = uiState.selectedLanguage,
            sassLevel = uiState.sassLevel,
            hasApiKey = uiState.hasApiKey,
            onVoiceChanged = { viewModel.updateVoice(it) },
            onLanguageChanged = { viewModel.updateLanguage(it) },
            onSassChanged = { viewModel.updateSassLevel(it) },
            onTestVoice = { viewModel.testVoiceSample() },
            onDismiss = { showSettingsDialog = false }
        )
    }
}
