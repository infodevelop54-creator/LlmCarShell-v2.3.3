package com.example.llmcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmcar.data.HistoryRepository
import com.example.llmcar.data.db.HistoryEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<HistoryEntity>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")) }

    LaunchedEffect(Unit) { repo.observe().collectLatest { entries = it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История запросов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.Delete, "Очистить", tint = Color(0xFFF44336))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E2E),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Color(0xFF1A1A2E)).padding(padding)
        ) {
            if (entries.isEmpty()) {
                Text("История пуста", color = Color(0xFF9090A0), fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { e ->
                        HistoryItem(
                            entry = e,
                            formattedDate = df.format(Date(e.timestamp)),
                            onDelete = { scope.launch { repo.delete(e.id) } }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все сохранённые запросы будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.clear() }
                    confirmClear = false
                }) { Text("Очистить", color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun HistoryItem(
    entry: HistoryEntity,
    formattedDate: String,
    onDelete: () -> Unit
) {
    Surface(color = Color(0xFF252535), shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formattedDate, fontSize = 11.sp, color = Color(0xFF9090A0),
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Удалить",
                        tint = Color(0xFF9090A0), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Вопрос:", fontSize = 11.sp, color = Color(0xFF7C4DFF),
                fontWeight = FontWeight.SemiBold)
            Text(entry.userText, fontSize = 14.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Ответ:", fontSize = 11.sp, color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold)
            Text(entry.assistantText, fontSize = 14.sp, color = Color(0xFFE0E0E0))
        }
    }
}