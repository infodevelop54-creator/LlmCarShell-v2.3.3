package com.example.llmcar.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmcar.service.ObdPollingService
import com.example.llmcar.ui.components.GaugeCard
import com.example.llmcar.util.AppSignals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var liveData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var status by remember { mutableStateOf("Остановлено") }
    var running by remember { mutableStateOf(false) }
    var mac by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        AppSignals.obdLive.collect { (s, data) ->
            status = s
            liveData = data
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBD Live Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E2E),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(Color(0xFF1A1A2E))
                .padding(padding).padding(16.dp)
        ) {
            OutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("MAC-адрес ELM327") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                enabled = !running
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val i = Intent(context, ObdPollingService::class.java)
                            .putExtra(ObdPollingService.EXTRA_MAC, mac)
                        context.startForegroundService(i)
                        running = true
                    },
                    enabled = !running && mac.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Старт")
                }
                OutlinedButton(
                    onClick = {
                        context.stopService(Intent(context, ObdPollingService::class.java))
                        running = false
                    },
                    enabled = running,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Стоп")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Статус: $status", fontSize = 12.sp, color = Color(0xFF9090A0))

            Spacer(Modifier.height(20.dp))

            if (liveData.isEmpty()) {
                Text("Нет данных. Подключитесь и нажмите Старт.",
                    fontSize = 14.sp, color = Color(0xFF808090),
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(liveData.entries.toList()) { (label, value) ->
                        GaugeCard(label = label, value = value)
                    }
                }
            }
        }
    }
}