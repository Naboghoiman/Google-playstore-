package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.audio.WavAudioRecorder
import com.example.ui.theme.RecordRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsDialog(
    recorder: WavAudioRecorder,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var recordingsList by remember { mutableStateOf(recorder.getSavedRecordings()) }
    var currentlyPlayingFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.80f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141E))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = RecordRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RECORDED DJ MIXES (${recordingsList.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (recordingsList.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No recorded mixes yet.\nTap REC on the DJ Console to record your live mix!",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recordingsList) { file ->
                            val isPlayingThis = currentlyPlayingFile?.absolutePath == file.absolutePath
                            val fileSizeMb = String.format(Locale.US, "%.1f MB", file.length() / (1024f * 1024f))
                            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(file.lastModified()))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF151B27))
                                    .border(1.dp, if (isPlayingThis) RecordRed else Color(0xFF232D40), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.nameWithoutExtension,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "$dateStr • $fileSizeMb",
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Play / Pause Button
                                        IconButton(
                                            onClick = {
                                                if (isPlayingThis) {
                                                    mediaPlayer?.pause()
                                                    currentlyPlayingFile = null
                                                } else {
                                                    mediaPlayer?.release()
                                                    mediaPlayer = MediaPlayer().apply {
                                                        setDataSource(file.absolutePath)
                                                        prepare()
                                                        start()
                                                        setOnCompletionListener {
                                                            currentlyPlayingFile = null
                                                        }
                                                    }
                                                    currentlyPlayingFile = file
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = if (isPlayingThis) RecordRed else Color.White
                                            )
                                        }

                                        // Share Button
                                        IconButton(
                                            onClick = { shareRecording(context, file) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF00E5FF))
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = {
                                                if (isPlayingThis) {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                    mediaPlayer = null
                                                    currentlyPlayingFile = null
                                                }
                                                file.delete()
                                                recordingsList = recorder.getSavedRecordings()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareRecording(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share DJ Mix"))
    } catch (e: Exception) {
        // Fallback standard send
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_TEXT, "DJ IMAN Mix: ${file.name}")
        }
        context.startActivity(Intent.createChooser(intent, "Share DJ Mix"))
    }
}
