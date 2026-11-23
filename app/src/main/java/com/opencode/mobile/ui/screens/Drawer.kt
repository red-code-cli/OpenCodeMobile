package com.opencode.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.AppViewModel
import com.opencode.mobile.data.Screen
import com.opencode.mobile.ui.theme.Oc

@Composable
fun DrawerContent(vm: AppViewModel, close: () -> Unit) {
    val c = Oc.c
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var filterUnviewed by remember { mutableStateOf(false) }

    val unviewedCount = remember(vm.sessions.size, vm.sessions.map { it.viewed }) {
        vm.sessions.count { !it.viewed }
    }
    val archivedCount = remember(vm.sessions.size, vm.sessions.map { it.archived }) {
        vm.sessions.count { it.archived }
    }

    val displayedSessions = remember(vm.sessions.toList(), searchQuery, filterUnviewed) {
        vm.sessions.filter { s ->
            (!filterUnviewed || !s.viewed) &&
            (searchQuery.isBlank() || s.title.contains(searchQuery, ignoreCase = true) || s.project.contains(searchQuery, ignoreCase = true))
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = c.bg,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(0.84f)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("OpenCode", color = c.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (showSearch) c.surfaceSel else c.surface)
                        .clickable { showSearch = !showSearch; if (!showSearch) searchQuery = "" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.text)
                }
            }

            if (showSearch) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).height(50.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.textDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.text),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> if (searchQuery.isEmpty()) Text("Filter sessions...", color = c.textDim, fontSize = 16.sp); inner() }
                    )
                }
            }

            NavRow(
                icon = Icons.Rounded.Layers,
                tint = c.badge,
                label = "Catch Up",
                count = unviewedCount.toString(),
                selected = filterUnviewed,
                onClick = { filterUnviewed = !filterUnviewed }
            )
            NavRow(
                icon = Icons.Rounded.Archive,
                tint = c.textDim,
                label = "Archived",
                count = archivedCount.toString(),
                selected = false,
                onClick = {}
            )

            Text("Recents", color = c.textDim, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 10.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(displayedSessions, key = { it.id }) { s ->
                    val sel = s.id == vm.currentSession
                    val bg by animateColorAsState(if (sel) c.surface else Color.Transparent, label = "sel")
                    val running = s.running

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
                            .clickable { vm.openSession(s.id); close() }.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF6E6E6E), Color(0xFF4A4A4A)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(s.project.firstOrNull()?.uppercase() ?: "O", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            s.title,
                            color = c.text,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (running) {
                            CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp), color = c.textDim, strokeWidth = 2.5.dp)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
                        .clickable { vm.newSession(); close() }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.EditNote, null, tint = c.text, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("New Session", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).clickable { close(); vm.push(Screen.Settings) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = c.text, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    count: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) c.surfaceSel else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = c.text, fontSize = 19.sp, modifier = Modifier.weight(1f))
        Text(count, color = c.textDim, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
    }
}
