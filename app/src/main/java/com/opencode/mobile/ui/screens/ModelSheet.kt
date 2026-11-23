package com.opencode.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.AppViewModel
import com.opencode.mobile.data.ModelOption
import com.opencode.mobile.ui.theme.Oc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val c = Oc.c
    var q by remember { mutableStateOf("") }
    val models = vm.rawModels

    val filtered = remember(models, q) {
        if (q.isBlank()) models else models.filter { it.name.contains(q, true) || it.provider.contains(q, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.7f).navigationBarsPadding().imePadding().padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).border(1.dp, c.divider, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.textDim)
                Spacer(Modifier.width(14.dp))
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 18.sp),
                    cursorBrush = SolidColor(c.text),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box { if (q.isEmpty()) Text("Search Models (${models.size})", color = c.textDim, fontSize = 18.sp); inner() }
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { "${it.provider}_${it.modelId}_${it.name}" }) { m ->
                    val isSel = m.name == vm.model.name && m.provider == vm.model.provider
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (isSel) c.surfaceSel else Color.Transparent)
                            .clickable { vm.model = m; onDismiss() }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.text.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
                            Text(m.provider.firstOrNull()?.toString() ?: "M", color = c.bg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, color = c.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.provider, color = c.textDim, fontSize = 15.sp)
                        }
                        if (m.variant != null) {
                            Text(m.variant, color = c.textDim, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
