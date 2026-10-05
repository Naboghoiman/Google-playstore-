package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.analysis.BeatGridMath
import com.example.audio.AutoScratchController
import com.example.audio.DjAudioEngine
import com.example.automix.AutomixController
import com.example.media.AudioDecoder
import com.example.media.MediaStoreRepository
import com.example.model.DeckId
import com.example.model.DeckState
import com.example.model.PadMode
import com.example.model.PcmTrack
import com.example.ui.components.AutomixDialog
import com.example.ui.components.Crossfader
import com.example.ui.components.DjRotaryKnob
import com.example.ui.components.FxControlPanel
import com.example.ui.components.LibraryDialog
import com.example.ui.components.PerformancePadsView
import com.example.ui.components.RecordingsDialog
import com.example.ui.components.SamplerBoardView
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TurntablePlatter
import com.example.ui.components.VerticalFader
import com.example.ui.components.VuMeter
import com.example.ui.components.WaveformView
import com.example.ui.theme.CueColor
import com.example.ui.theme.DeckABackground
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBBackground
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.DjDarkBg
import com.example.ui.theme.DjPanelBg
import com.example.ui.theme.PlayColor
import com.example.ui.theme.RecordRed
import com.example.ui.theme.SyncColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun DjMainScreen(
    audioEngine: DjAudioEngine,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val deckAState by audioEngine.deckAState.collectAsState()
    val deckBState by audioEngine.deckBState.collectAsState()
    val mixerState by audioEngine.mixerState.collectAsState()

    val scratchController = remember { AutoScratchController(audioEngine, scope) }
    val automixController = remember { AutomixController(audioEngine, scope) }
    val isAutomixActive by automixController.isAutomixRunning.collectAsState()
    val currentTransition by automixController.currentTransition.collectAsState()

    val audioDecoder = remember { AudioDecoder(context) }
    val mediaStoreRepo = remember { MediaStoreRepository(context) }
    var deviceTracks by remember { mutableStateOf(listOf<com.example.model.TrackInfo>()) }

    // Dialogs state
    var showLibraryDialog by remember { mutableStateOf(false) }
    var showRecordingsDialog by remember { mutableStateOf(false) }
    var showAutomixDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Bottom performance tab: 0 = DECK A PADS, 1 = DECK B PADS, 2 = MASTER FX, 3 = 16-PAD SAMPLER
    var selectedPerformanceTab by remember { mutableIntStateOf(0) }

    // Auto-load default initial demo tracks on startup
    LaunchedEffect(Unit) {
        audioEngine.start()
        val demos = audioEngine.demoTracks
        if (demos.isNotEmpty()) {
            audioEngine.loadTrack(DeckId.DECK_A, demos[0])
            if (demos.size > 1) {
                audioEngine.loadTrack(DeckId.DECK_B, demos[1])
            }
        }
        deviceTracks = mediaStoreRepo.loadDeviceTracks()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DjDarkBg)
    ) {
        // =========================================================================
        // TOP GLOBAL TOOLBAR (App Title, Record, Recordings, Automix, Library, Settings)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1017))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Branding
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(DeckAPrimary)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DJ IMAN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(DeckBPrimary)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(text = "3.4", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                    Text(
                        text = "ADVANCE STUDIO",
                        fontSize = 7.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RECORD MIX BUTTON
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (mixerState.isRecording) RecordRed else Color(0xFF261217))
                        .border(1.dp, if (mixerState.isRecording) Color.White else RecordRed.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable {
                            val file = audioEngine.toggleRecording()
                            if (mixerState.isRecording) {
                                Toast.makeText(context, "Mix recording saved: ${file?.name}", Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (mixerState.isRecording) Color.White else RecordRed,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (mixerState.isRecording) "REC ON" else "REC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mixerState.isRecording) Color.White else RecordRed
                        )
                    }
                }

                // RECORDINGS LIST
                ToolbarButton(
                    icon = Icons.Default.Folder,
                    text = "MIXES",
                    tint = Color(0xFFFFD600),
                    onClick = { showRecordingsDialog = true }
                )

                // AUTOMIX
                ToolbarButton(
                    icon = Icons.Default.Tune,
                    text = if (isAutomixActive) "AUTOMIX ON" else "AUTOMIX",
                    tint = if (isAutomixActive) PlayColor else SyncColor,
                    isActive = isAutomixActive,
                    onClick = { showAutomixDialog = true }
                )

                // CRATE / LIBRARY
                ToolbarButton(
                    icon = Icons.Default.LibraryMusic,
                    text = "TRACKS",
                    tint = DeckAPrimary,
                    onClick = { showLibraryDialog = true }
                )

                // SETTINGS
                ToolbarButton(
                    icon = Icons.Default.Settings,
                    text = "",
                    tint = TextSecondary,
                    onClick = { showSettingsDialog = true }
                )
            }
        }

        // =========================================================================
        // DUAL DECK WAVEFORM STACKS (DECK A TOP, DECK B BOTTOM)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0C11))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            WaveformView(
                deckState = deckAState,
                primaryColor = DeckAPrimary,
                onSeekToMs = { audioEngine.seekToMs(DeckId.DECK_A, it) }
            )
            WaveformView(
                deckState = deckBState,
                primaryColor = DeckBPrimary,
                onSeekToMs = { audioEngine.seekToMs(DeckId.DECK_B, it) }
            )
        }

        // =========================================================================
        // MAIN CONSOLE SECTION (Deck A Platter | Central Mixer | Deck B Platter)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .background(DjPanelBg)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // ---- DECK A (LEFT PLATTER & TRANSPORT) ----
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DeckABackground)
                    .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(6.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                DeckHeaderStrip(
                    deckState = deckAState,
                    deckName = "DECK A",
                    accentColor = DeckAPrimary,
                    onSyncClick = { audioEngine.syncDecks(DeckId.DECK_B, DeckId.DECK_A) }
                )

                // Turntable Platter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TurntablePlatter(
                        deckId = DeckId.DECK_A,
                        isPlaying = deckAState.isPlaying,
                        rotationAngle = deckAState.platterAngleDegrees,
                        primaryColor = DeckAPrimary,
                        secondaryColor = Color(0xFF0091EA),
                        onPlatterTouch = { audioEngine.onPlatterTouch(DeckId.DECK_A, it) },
                        onPlatterRotate = { audioEngine.onPlatterRotate(DeckId.DECK_A, it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Transport (CUE & PLAY)
                TransportButtonsRow(
                    deckId = DeckId.DECK_A,
                    isPlaying = deckAState.isPlaying,
                    onCueDown = { audioEngine.onCueDown(DeckId.DECK_A) },
                    onCueUp = { audioEngine.onCueUp(DeckId.DECK_A) },
                    onPlayToggle = { audioEngine.togglePlay(DeckId.DECK_A) },
                    onSetCue = { audioEngine.setCuePoint(DeckId.DECK_A) }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // ---- CENTRAL MIXER CONSOLE ----
            Column(
                modifier = Modifier
                    .width(170.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF121520))
                    .border(1.dp, Color(0xFF262E40), RoundedCornerShape(6.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Master Volume & Master Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DjRotaryKnob(
                        label = "MASTER",
                        value = mixerState.masterVolume,
                        onValueChange = { audioEngine.setMasterVolume(it) },
                        minVal = 0f,
                        maxVal = 1f,
                        size = 38.dp,
                        activeColor = Color.White
                    )

                    // Master stereo VU Meter
                    VuMeter(
                        levelL = mixerState.masterVuLeft,
                        levelR = mixerState.masterVuRight,
                        meterWidth = 14.dp,
                        modifier = Modifier.height(42.dp)
                    )
                }

                // Channel EQs & Filters in 2 columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Channel A EQs & Filter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text("CH A", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = DeckAPrimary)
                        DjRotaryKnob(
                            label = "HI",
                            value = deckAState.eqHighGainDb,
                            onValueChange = { audioEngine.setEqGain(DeckId.DECK_A, "HIGH", it) },
                            minVal = -26f,
                            maxVal = 6f,
                            size = 34.dp,
                            activeColor = DeckAPrimary
                        )
                        DjRotaryKnob(
                            label = "MID",
                            value = deckAState.eqMidGainDb,
                            onValueChange = { audioEngine.setEqGain(DeckId.DECK_A, "MID", it) },
                            minVal = -26f,
                            maxVal = 6f,
                            size = 34.dp,
                            activeColor = DeckAPrimary
                        )
                        DjRotaryKnob(
                            label = "LOW",
                            value = deckAState.eqLowGainDb,
                            onValueChange = { audioEngine.setEqGain(DeckId.DECK_A, "LOW", it) },
                            minVal = -26f,
                            maxVal = 6f,
                            size = 34.dp,
                            activeColor = DeckAPrimary
                        )
                        DjRotaryKnob(
                            label = "FILTER",
                            value = deckAState.filterKnob,
                            onValueChange = { audioEngine.setFilterKnob(DeckId.DECK_A, it) },
                            minVal = -1f,
                            maxVal = 1f,
                            size = 34.dp,
                            activeColor = DeckAPrimary
                        )
                    }

                    // Channel B EQs & Filter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text("CH B", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = DeckBPrimary)
                        DjRotaryKnob(
                            label = "HI",
                            value = deckBState.eqHighGainDb,
                            onValueChange = { audioEngine.setEqGain(DeckId.DECK_B, "HIGH", it) },
                            minVal = -26f,
                            maxVal = 6f,
                            size = 34.dp,
                            activeColor = DeckBPrimary
                        )
                        DjRotaryKnob(
                            label = "MID",
                            value = deckBState.eqMidGainDb,
                            onValueChange = { audioEngine.setEqGain(DeckId.DECK_B, "MID", it) },
                            minVal = -26f,
                            maxVal = 6f,
                            size = 34.dp,
                            activeColor = DeckBPrimary
                        )
                        DjRotaryKnob(
                            label = "LOW",
                            value = deckBState.eqLowGainDb,
                            onValueChange = { audioEngine.setEqGain(DeckId.DECK_B, "LOW", it) },
                            minVal = -26f,
                            maxVal = 6f,
                            size = 34.dp,
                            activeColor = DeckBPrimary
                        )
                        DjRotaryKnob(
                            label = "FILTER",
                            value = deckBState.filterKnob,
                            onValueChange = { audioEngine.setFilterKnob(DeckId.DECK_B, it) },
                            minVal = -1f,
                            maxVal = 1f,
                            size = 34.dp,
                            activeColor = DeckBPrimary
                        )
                    }
                }

                // Volume Faders and Channel VUs
                Row(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerticalFader(
                            value = deckAState.volumeFader,
                            onValueChange = { audioEngine.setVolumeFader(DeckId.DECK_A, it) },
                            activeColor = DeckAPrimary,
                            faderWidth = 24.dp
                        )
                        VuMeter(
                            levelL = deckAState.vuLevelLeft,
                            levelR = deckAState.vuLevelRight,
                            meterWidth = 8.dp,
                            modifier = Modifier.height(60.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VuMeter(
                            levelL = deckBState.vuLevelLeft,
                            levelR = deckBState.vuLevelRight,
                            meterWidth = 8.dp,
                            modifier = Modifier.height(60.dp)
                        )
                        VerticalFader(
                            value = deckBState.volumeFader,
                            onValueChange = { audioEngine.setVolumeFader(DeckId.DECK_B, it) },
                            activeColor = DeckBPrimary,
                            faderWidth = 24.dp
                        )
                    }
                }

                // Horizontal Crossfader
                Crossfader(
                    position = mixerState.crossfaderPosition,
                    curve = mixerState.crossfaderCurve,
                    isReversed = mixerState.isHamsterReversed,
                    onPositionChange = { audioEngine.setCrossfaderPosition(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // ---- DECK B (RIGHT PLATTER & TRANSPORT) ----
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DeckBBackground)
                    .border(1.dp, Color(0xFF42271E), RoundedCornerShape(6.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                DeckHeaderStrip(
                    deckState = deckBState,
                    deckName = "DECK B",
                    accentColor = DeckBPrimary,
                    onSyncClick = { audioEngine.syncDecks(DeckId.DECK_A, DeckId.DECK_B) }
                )

                // Turntable Platter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TurntablePlatter(
                        deckId = DeckId.DECK_B,
                        isPlaying = deckBState.isPlaying,
                        rotationAngle = deckBState.platterAngleDegrees,
                        primaryColor = DeckBPrimary,
                        secondaryColor = Color(0xFFFF3D00),
                        onPlatterTouch = { audioEngine.onPlatterTouch(DeckId.DECK_B, it) },
                        onPlatterRotate = { audioEngine.onPlatterRotate(DeckId.DECK_B, it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Transport (CUE & PLAY)
                TransportButtonsRow(
                    deckId = DeckId.DECK_B,
                    isPlaying = deckBState.isPlaying,
                    onCueDown = { audioEngine.onCueDown(DeckId.DECK_B) },
                    onCueUp = { audioEngine.onCueUp(DeckId.DECK_B) },
                    onPlayToggle = { audioEngine.togglePlay(DeckId.DECK_B) },
                    onSetCue = { audioEngine.setCuePoint(DeckId.DECK_B) }
                )
            }
        }

        // =========================================================================
        // BOTTOM PERFORMANCE PANELS (Tabs: Deck A Pads, Deck B Pads, Master FX, Sampler)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0E14))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            // Tab Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    Pair(0, "DECK A PADS"),
                    Pair(1, "DECK B PADS"),
                    Pair(2, "MASTER FX"),
                    Pair(3, "16-PAD SAMPLER")
                )
                tabs.forEach { (index, title) ->
                    val isSelected = selectedPerformanceTab == index
                    val tabColor = when (index) {
                        0 -> DeckAPrimary
                        1 -> DeckBPrimary
                        2 -> SyncColor
                        else -> Color(0xFFFFD600)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) tabColor.copy(alpha = 0.2f) else Color(0xFF131722))
                            .border(1.dp, if (isSelected) tabColor else Color(0xFF202738), RoundedCornerShape(4.dp))
                            .clickable { selectedPerformanceTab = index }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Active Tab Content
            when (selectedPerformanceTab) {
                0 -> {
                    PerformancePadsView(
                        deckState = deckAState,
                        onPadModeSelected = { audioEngine.setPadMode(DeckId.DECK_A, it) },
                        onHotCueTriggered = { idx, clear -> audioEngine.triggerHotCue(DeckId.DECK_A, idx, clear) },
                        onAutoLoopTriggered = { audioEngine.toggleAutoLoop(DeckId.DECK_A, it) },
                        onBeatJumpTriggered = { b, f -> audioEngine.beatJump(DeckId.DECK_A, b, f) },
                        onAutoScratchTriggered = { scratchController.startPattern(DeckId.DECK_A, it, deckAState.effectiveBpm) },
                        onKeyShiftTriggered = { /* Key shift handled via pitch */ }
                    )
                }
                1 -> {
                    PerformancePadsView(
                        deckState = deckBState,
                        onPadModeSelected = { audioEngine.setPadMode(DeckId.DECK_B, it) },
                        onHotCueTriggered = { idx, clear -> audioEngine.triggerHotCue(DeckId.DECK_B, idx, clear) },
                        onAutoLoopTriggered = { audioEngine.toggleAutoLoop(DeckId.DECK_B, it) },
                        onBeatJumpTriggered = { b, f -> audioEngine.beatJump(DeckId.DECK_B, b, f) },
                        onAutoScratchTriggered = { scratchController.startPattern(DeckId.DECK_B, it, deckBState.effectiveBpm) },
                        onKeyShiftTriggered = { /* Key shift handled via pitch */ }
                    )
                }
                2 -> {
                    FxControlPanel(
                        mixerState = mixerState,
                        onFxTypeSelected = { audioEngine.setMasterFxType(it) },
                        onToggleFx = { audioEngine.toggleMasterFx() },
                        onDryWetChanged = { audioEngine.setFxDryWet(it) },
                        onFxParamsChanged = { x, y -> audioEngine.setFxParams(x, y) },
                        onBeatDivisionChanged = { audioEngine.setFxBeatDivision(it) }
                    )
                }
                3 -> {
                    SamplerBoardView(
                        onTriggerSample = { padIndex -> audioEngine.triggerSamplerPad(padIndex) }
                    )
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS & DRAWERS
    // =========================================================================

    if (showLibraryDialog) {
        LibraryDialog(
            demoTracks = audioEngine.demoTracks,
            deviceTracks = deviceTracks,
            onLoadDemoToDeck = { deckId, track -> audioEngine.loadTrack(deckId, track) },
            onLoadUriToDeck = { deckId, uri, title, artist ->
                scope.launch {
                    val pcmTrack = audioDecoder.decodeFromUri(uri, "user_${System.currentTimeMillis()}", title, artist)
                    if (pcmTrack != null) {
                        audioEngine.loadTrack(deckId, pcmTrack)
                    } else {
                        Toast.makeText(context, "Could not decode audio file.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showLibraryDialog = false }
        )
    }

    if (showRecordingsDialog) {
        RecordingsDialog(
            recorder = audioEngine.recorder,
            onDismiss = { showRecordingsDialog = false }
        )
    }

    if (showAutomixDialog) {
        AutomixDialog(
            isRunning = isAutomixActive,
            currentTransition = currentTransition,
            onTransitionChanged = { automixController.setTransition(it) },
            onStartAutomix = { beats -> automixController.startAutomix(beats) },
            onStopAutomix = { automixController.stopAutomix() },
            onDismiss = { showAutomixDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            deckAState = deckAState,
            mixerState = mixerState,
            onPitchRangeChanged = {
                audioEngine.setPitchRange(DeckId.DECK_A, it)
                audioEngine.setPitchRange(DeckId.DECK_B, it)
            },
            onCrossfaderCurveChanged = { audioEngine.setCrossfaderCurve(it) },
            onHamsterToggled = { /* handled in engine */ },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

// =========================================================================
// SUB-COMPONENTS
// =========================================================================

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) tint.copy(alpha = 0.25f) else Color(0xFF161B26))
            .border(1.dp, if (isActive) tint else Color(0xFF222B3D), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isActive) tint else TextPrimary)
            }
        }
    }
}

@Composable
private fun DeckHeaderStrip(
    deckState: DeckState,
    deckName: String,
    accentColor: Color,
    onSyncClick: () -> Unit
) {
    val track = deckState.track
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deck Badge and Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(text = deckName, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = track?.title ?: "No Track Loaded",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.width(90.dp)
                )
            }

            // Sync Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (deckState.isSyncActive) SyncColor else Color(0xFF1A1A24))
                    .border(1.dp, if (deckState.isSyncActive) Color.White else SyncColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .clickable { onSyncClick() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SYNC",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (deckState.isSyncActive) Color.Black else SyncColor
                )
            }
        }

        // BPM, Key, Time
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${String.format("%.1f", deckState.effectiveBpm)} BPM",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD600)
            )
            Text(
                text = "KEY: ${track?.initialKey ?: "--"}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD500F9)
            )
            val posSec = (deckState.currentPositionMs / 1000.0).toInt()
            val totalSec = (deckState.durationMs / 1000.0).toInt()
            Text(
                text = "${posSec / 60}:${String.format("%02d", posSec % 60)} / ${totalSec / 60}:${String.format("%02d", totalSec % 60)}",
                fontSize = 8.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TransportButtonsRow(
    deckId: DeckId,
    isPlaying: Boolean,
    onCueDown: () -> Unit,
    onCueUp: () -> Unit,
    onPlayToggle: () -> Unit,
    onSetCue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CUE BUTTON
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CueColor.copy(alpha = 0.2f))
                .border(1.5.dp, CueColor, RoundedCornerShape(6.dp))
                .clickable { onPlayToggle() /* or onSetCue */ }
                .padding(2.dp)
        ) {
            Text(
                text = "CUE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = CueColor
            )
        }

        // PLAY / PAUSE BUTTON
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isPlaying) PlayColor else PlayColor.copy(alpha = 0.25f))
                .border(1.5.dp, PlayColor, RoundedCornerShape(6.dp))
                .clickable { onPlayToggle() }
                .padding(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying) Color.Black else PlayColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (isPlaying) "PAUSE" else "PLAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isPlaying) Color.Black else PlayColor
                )
            }
        }
    }
}
