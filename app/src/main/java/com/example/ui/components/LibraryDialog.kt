package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.DeckId
import com.example.model.PcmTrack
import com.example.model.TrackInfo
import com.example.ui.theme.DeckAPrimary
import com.example.ui.theme.DeckBPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LibraryDialog(
    demoTracks: List<PcmTrack>,
    deviceTracks: List<TrackInfo>,
    onLoadDemoToDeck: (DeckId, PcmTrack) -> Unit,
    onLoadUriToDeck: (DeckId, Uri, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Demo DJ Anthems, 1 = Device Music
    var searchQuery by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "User Audio"
            onLoadUriToDeck(DeckId.DECK_A, it, fileName, "Local File")
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141F))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = DeckAPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DJ TRACK CRATE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row {
                        // Open external file picker button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF22293A))
                                .clickable { filePickerLauncher.launch("audio/*") }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = null,
                                    tint = DeckAPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OPEN FILE", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by title, artist, BPM, or Key (e.g. 8A, 128)", fontSize = 11.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeckAPrimary,
                        unfocusedBorderColor = Color(0xFF22293A),
                        focusedContainerColor = Color(0xFF0C0E14),
                        unfocusedContainerColor = Color(0xFF0C0E14),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: Demo Tracks vs Device MediaStore
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF161B26),
                    contentColor = DeckAPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = DeckAPrimary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("DEMO CLUB TRACKS (${demoTracks.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("DEVICE AUDIO (${deviceTracks.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track List
                if (selectedTab == 0) {
                    val filteredDemos = demoTracks.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true) ||
                                it.initialKey.contains(searchQuery, ignoreCase = true) ||
                                it.bpm.toString().contains(searchQuery)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredDemos) { track ->
                            CrateTrackItem(
                                title = track.title,
                                artist = track.artist,
                                bpm = track.bpm,
                                key = track.initialKey,
                                duration = String.format("%.0fs", track.durationMs / 1000.0),
                                onLoadA = {
                                    onLoadDemoToDeck(DeckId.DECK_A, track)
                                    onDismiss()
                                },
                                onLoadB = {
                                    onLoadDemoToDeck(DeckId.DECK_B, track)
                                    onDismiss()
                                }
                            )
                        }
                    }
                } else {
                    val filteredDevice = deviceTracks.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredDevice.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (deviceTracks.isEmpty()) "No local music found on device.\nTap OPEN FILE to select an audio file." else "No matches found.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredDevice) { trackInfo ->
                                val uri = trackInfo.uriString?.let { Uri.parse(it) }
                                CrateTrackItem(
                                    title = trackInfo.title,
                                    artist = trackInfo.artist,
                                    bpm = trackInfo.bpm,
                                    key = trackInfo.musicalKey,
                                    duration = "${trackInfo.durationSeconds / 60}:${String.format("%02d", trackInfo.durationSeconds % 60)}",
                                    onLoadA = {
                                        if (uri != null) {
                                            onLoadUriToDeck(DeckId.DECK_A, uri, trackInfo.title, trackInfo.artist)
                                            onDismiss()
                                        }
                                    },
                                    onLoadB = {
                                        if (uri != null) {
                                            onLoadUriToDeck(DeckId.DECK_B, uri, trackInfo.title, trackInfo.artist)
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrateTrackItem(
    title: String,
    artist: String,
    bpm: Double,
    key: String,
    duration: String,
    onLoadA: () -> Unit,
    onLoadB: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141A26))
            .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = artist, fontSize = 9.sp, color = TextSecondary, maxLines = 1)
                    Text(text = " • ", fontSize = 9.sp, color = TextMuted)
                    Text(text = "$duration", fontSize = 9.sp, color = TextMuted)
                }
            }

            // BPM and Key Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1F293D))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "${bpm.toInt()} BPM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD600))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A1C36))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = key, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD500F9))
                }

                // Load Deck A Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeckAPrimary.copy(alpha = 0.2f))
                        .border(1.dp, DeckAPrimary, RoundedCornerShape(4.dp))
                        .clickable { onLoadA() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("LOAD A", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DeckAPrimary)
                }

                // Load Deck B Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeckBPrimary.copy(alpha = 0.2f))
                        .border(1.dp, DeckBPrimary, RoundedCornerShape(4.dp))
                        .clickable { onLoadB() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("LOAD B", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DeckBPrimary)
                }
            }
        }
    }
}
