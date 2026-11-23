package com.opencode.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.*
import com.opencode.mobile.ui.*
import com.opencode.mobile.ui.theme.Oc
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val c = Oc.c
    val id = vm.currentSession ?: return
    val session = vm.sessions.firstOrNull { it.id == id } ?: return
    val msgs = vm.messages[id].orEmpty()
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val total = msgs.size + if (session.running) 1 else 0

    LaunchedEffect(id, total) {
        if (total > 0) state.animateScrollToItem(total - 1)
    }

    val atBottom by remember {
        derivedStateOf {
            val li = state.layoutInfo
            li.totalItemsCount == 0 || (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 1
        }
    }

    val sessionPerms = remember(vm.permissions, id) { vm.permissions.filter { it.sessionId == id } }
    val sessionForms = remember(vm.forms, id) { vm.forms.filter { it.sessionId == id } }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        OcTopBar(
            title = session.title,
            subtitle = session.project,
            onMenu = onMenu,
            connectionState = vm.connectionState
        ) {
            Box {
                ProgressMenuButton(running = session.running) { menu = true }
                SessionMenu(vm, session, menu) { menu = false }
            }
        }
        HorizontalDivider(color = c.divider.copy(alpha = if (state.canScrollBackward) 1f else 0f))
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Pending Permissions Cards
                itemsIndexed(sessionPerms, key = { _, p -> "perm_${p.id}" }) { _, p ->
                    PermissionCard(p, onReply = { decision -> vm.replyPermission(p.id, decision) })
                }

                // Pending Forms Cards
                itemsIndexed(sessionForms, key = { _, f -> "form_${f.id}" }) { _, f ->
                    FormCard(f, onReply = { answers -> vm.replyForm(f.id, answers) })
                }

                // Messages
                itemsIndexed(msgs, key = { _, m -> m.id }) { _, m ->
                    MessageItem(m, Modifier.animateItem())
                }

                // Running Indicator
                if (session.running) {
                    item(key = "working") {
                        Row(
                            Modifier.animateItem().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DotSpinner()
                            Spacer(Modifier.width(14.dp))
                            Text("Working", color = c.text, fontSize = 20.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { vm.stop() },
                                colors = ButtonDefaults.textButtonColors(contentColor = c.danger)
                            ) {
                                Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                !atBottom, Modifier.align(Alignment.BottomEnd).padding(18.dp),
                enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()
            ) {
                Box(
                    Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(14.dp))
                        .clickable { scope.launch { state.animateScrollToItem((total - 1).coerceAtLeast(0)) } },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.ArrowDownward, null, tint = c.text) }
            }
        }
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp, top = 4.dp)) {
            Composer(
                vm = vm,
                text = text,
                onText = { text = it },
                showPickers = false,
                recording = recording,
                onRecording = { recording = it },
                onModelClick = {},
                onSend = { vm.send(it); text = "" }
            )
        }
    }
}

@Composable
private fun PermissionCard(p: PermissionRequest, onReply: (String) -> Unit) {
    val c = Oc.c
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceHigh).border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Security, null, tint = c.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Tool Permission Request", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text("Tool: ${p.tool}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (p.input.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(p.input, color = c.textDim, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onReply("allow") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                modifier = Modifier.weight(1f)
            ) { Text("Allow Once", fontSize = 15.sp) }
            OutlinedButton(
                onClick = { onReply("always") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Always", fontSize = 15.sp) }
            TextButton(
                onClick = { onReply("deny") },
                colors = ButtonDefaults.textButtonColors(contentColor = c.danger)
            ) { Text("Reject", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun FormCard(f: QuestionForm, onReply: (List<String>) -> Unit) {
    val c = Oc.c
    var answer by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceHigh).border(1.dp, c.divider, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HelpOutline, null, tint = c.badge, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(f.title.ifBlank { "Agent Question" }, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        f.questions.forEach { q ->
            Text(q, color = c.text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            placeholder = { Text("Your answer...", color = c.textDim) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onReply(listOf(answer)) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White)
            ) { Text("Submit Answer", fontSize = 15.sp) }
        }
    }
}

private fun inlineCode(text: String, bg: Color) = buildAnnotatedString {
    text.split("`").forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(background = bg, fontFamily = FontFamily.Monospace)) { append(" $p ") } else append(p)
    }
}

@Composable
private fun MessageItem(m: Msg, modifier: Modifier) {
    val c = Oc.c
    when (m) {
        is UserMsg -> Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text("Today ${m.time}", color = c.textDim, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.clip(RoundedCornerShape(18.dp)).background(c.surfaceHigh).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(m.text, color = c.text, fontSize = 19.sp)
            }
            if (m.isOptimistic) {
                Text("Sending...", color = c.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, end = 6.dp))
            }
        }
        is AssistantMsg -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            m.parts.forEach { p ->
                when (p) {
                    is Part.Text -> Text(inlineCode(p.text, c.surfaceHigh), color = c.text, fontSize = 18.sp, lineHeight = 28.sp)
                    is Part.Reasoning -> ReasoningBlock(p.text)
                    is Part.Status -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, color = c.textDim, fontSize = 16.sp)
                        if (p.tokens != null) {
                            Text(" · ${p.tokens.input + p.tokens.output} tokens", color = c.textDim, fontSize = 14.sp)
                        }
                        if (p.cost != null && p.cost > 0) {
                            Text(" · \$${String.format("%.4f", p.cost)}", color = c.textDim, fontSize = 14.sp)
                        }
                    }
                    is Part.Tools -> ToolGroup(p.calls)
                    is Part.Diff -> DiffCard(p)
                    is Part.FileAttachment -> FileAttachmentRow(p)
                }
            }
            if (!m.error.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.danger.copy(alpha = 0.15f)).border(1.dp, c.danger, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(m.error, color = c.danger, fontSize = 15.sp)
                }
            }
            val clip = LocalClipboardManager.current
            val all = m.parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text }
            if (all.isNotBlank()) {
                Icon(
                    Icons.Rounded.ContentCopy, null,
                    tint = c.textDim,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable { clip.setText(AnnotatedString(all)) }
                )
            }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "rot")

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh.copy(alpha = 0.6f)).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Psychology, null, tint = c.textDim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Thinking", color = c.textDim, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, null, tint = c.textDim, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(open) {
            Text(
                text,
                color = c.textDim,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun DiffCard(diff: Part.Diff) {
    val c = Oc.c
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Difference, null, tint = c.badge, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(diff.filePath, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (diff.additions > 0) Text("+${diff.additions}", color = c.success, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
            if (diff.deletions > 0) Text("-${diff.deletions}", color = c.danger, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FileAttachmentRow(file: Part.FileAttachment) {
    val c = Oc.c
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AttachFile, null, tint = c.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(file.name, color = c.text, fontSize = 15.sp)
    }
}

@Composable
private fun ToolGroup(calls: List<ToolCall>) {
    val c = Oc.c
    var open by remember { mutableStateOf(true) }
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "rot")
    Column {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("Used ${calls.size} tools", color = c.textDim, fontSize = 18.sp)
            Icon(Icons.Rounded.ExpandMore, null, tint = c.textDim, modifier = Modifier.padding(start = 6.dp).size(24.dp).rotate(rot))
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(shape).border(1.dp, c.divider, shape).background(c.surface.copy(alpha = .6f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                calls.forEach { call ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusIcon = when (call.status) {
                                ToolStatus.COMPLETED -> Icons.Rounded.CheckCircle
                                ToolStatus.RUNNING, ToolStatus.PENDING -> Icons.Rounded.Sync
                                ToolStatus.ERROR -> Icons.Rounded.ErrorOutline
                            }
                            val statusColor = when (call.status) {
                                ToolStatus.COMPLETED -> c.success
                                ToolStatus.RUNNING, ToolStatus.PENDING -> Color(0xFFFF9500)
                                ToolStatus.ERROR -> c.danger
                            }
                            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(call.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(10.dp))
                            Text(call.cmd, color = c.textDim, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                        if (!call.output.isNullOrBlank()) {
                            Text(
                                call.output,
                                color = c.textDim,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 30.dp, top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMenu(vm: AppViewModel, s: Session, open: Boolean, dismiss: () -> Unit) {
    val c = Oc.c
    var pinned by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current

    @Composable
    fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color = c.text, onClick: () -> Unit) {
        DropdownMenuItem(
            text = { Text(label, color = color, fontSize = 19.sp) },
            leadingIcon = { Icon(icon, null, tint = if (color == c.text) c.textDim else color) },
            onClick = onClick,
            modifier = Modifier.heightIn(min = 58.dp)
        )
    }

    DropdownMenu(open, dismiss, containerColor = c.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(min = 250.dp)) {
        item(Icons.Rounded.PushPin, if (pinned) "Unpin" else "Pin") { pinned = !pinned; dismiss() }
        item(Icons.Rounded.Edit, "Rename") { dismiss(); rename = true }
        item(Icons.Rounded.ContentCopy, "Copy Session ID") { clip.setText(AnnotatedString(s.id)); dismiss() }
        HorizontalDivider(color = c.divider)
        item(Icons.Rounded.UnfoldLess, "Compact") { dismiss(); vm.compact(s.id) }
        item(Icons.Rounded.CallSplit, "Fork") { dismiss(); vm.fork(s.id) }
        item(Icons.Rounded.Info, "Session Info") { dismiss(); info = true }
        HorizontalDivider(color = c.divider)
        item(Icons.Rounded.Delete, "Delete Session", color = c.danger) { dismiss(); vm.delete(s.id) }
    }
    if (rename) {
        var t by remember { mutableStateOf(s.title) }
        AlertDialog(
            onDismissRequest = { rename = false }, containerColor = c.surface,
            title = { Text("Rename", color = c.text) },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.rename(s.id, t); rename = false }) { Text("Save") } },
            dismissButton = { TextButton({ rename = false }) { Text("Cancel") } }
        )
    }
    if (info) AlertDialog(
        onDismissRequest = { info = false }, containerColor = c.surface,
        title = { Text("Session Info", color = c.text) },
        text = { Text("ID: ${s.id}\nProject: ${s.project}\nDirectory: ${s.directory}\nAgent: ${vm.agent}\nModel: ${vm.model.name}", color = c.textDim) },
        confirmButton = { TextButton({ info = false }) { Text("OK") } }
    )
}
