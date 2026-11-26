package com.mogg.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mogg.app.ui.theme.Accent
import com.mogg.app.ui.theme.Warn

@Composable
fun GuidesScreen() {
    var openIndex by rememberSaveable { mutableStateOf(-1) }
    if (openIndex >= 0) {
        BackHandler { openIndex = -1 }
        GuideDetail(Guides[openIndex]) { openIndex = -1 }
        return
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        item {
            Text("Гайды", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("Лицо, кожа, сон, тело, стиль", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        val grouped = Guides.withIndex().groupBy { it.value.section }
        grouped.forEach { (section, list) ->
            item { Text(section.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
            items(list, key = { it.index }) { (i, g) ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { openIndex = i }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GuideDetail(g: Guide, onBack: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(g.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        g.body.trim().lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(4.dp))
                line.startsWith("## ") -> Text(line.removePrefix("## "), color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                line.startsWith("- ") -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", color = Accent)
                    Text(line.removePrefix("- "), lineHeight = 22.sp)
                }
                line.startsWith("! ") -> Text(
                    line.removePrefix("! "), color = Warn, lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().background(Color(0xFF2A2114), RoundedCornerShape(12.dp)).padding(12.dp)
                )
                else -> Text(line, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
