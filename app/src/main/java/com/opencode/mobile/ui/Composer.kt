package com.opencode.mobile.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.AppViewModel
import com.opencode.mobile.ui.theme.Oc

private data class PopupItem(val icon: ImageVector, val label: String, val hint: String, val insert: String)

private fun popupItems(text: String, vm: AppViewModel): List<PopupItem> {
    if (text.startsWith("/") && !text.contains(' ')) {
        return vm.commands.filter { it.name.startsWith(text, ignoreCase = true) }
            .map { PopupItem(Icons.Rounded.Code, it.name, it.hint, it.name + " ") }.reversed()
    }
    if (text.startsWith("@") && !text.contains(' ')) {
        val q = text.drop(1)
        val s = vm.sessions.take(3).map { PopupItem(Icons.Rounded.ChatBubble, it.title, it.project, "@${it.title} ") }
        val a = vm.agents.map { PopupItem(Icons.Rounded.SmartToy, it, "Agent", "@$it ") }
        return (s + a).filter { it.label.contains(q, ignoreCase = true) }.take(6).reversed()
    }
    return emptyList()
}

@Composable
fun Composer(
    vm: AppViewModel,
    text: String,
    onText: (String) -> Unit,
    showPickers: Boolean,
    recording: Boolean,
    onRecording: (Boolean) -> Unit,
    onModelClick: () -> Unit,
    onSend: (String) -> Unit,
) {
    val c = Oc.c
    val items = remember(text, vm.sessions.size, vm.commands.size) { popupItems(text, vm) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.surface).animateContentSize(tween(220))
    ) {
        AnimatedVisibility(items.isNotEmpty() && !recording, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
                items.forEachIndexed { i, it ->
                    val sel = i == items.lastIndex
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) c.surfaceSel else Color.Transparent)
                            .clickable { onText(it.insert) }.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(c.text), contentAlignment = Alignment.Center) {
                            Icon(it.icon, null, tint = c.bg, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(it.label, color = c.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(16.dp))
                        Text(it.hint, color = c.textDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        AnimatedContent(
            targetState = recording,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(120)) },
            label = "rec"
        ) { rec ->
            if (rec) RecordingBar(
                onCancel = { onRecording(false) },
                onStop = { onRecording(false) },
                onSend = { onRecording(false) }
            ) else InputArea(vm, text, onText, showPickers, onRecording, onModelClick, onSend)
        }
    }
}

@Composable
private fun RecordingBar(onCancel: () -> Unit, onStop: () -> Unit, onSend: () -> Unit) {
    val c = Oc.c
    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(Icons.Rounded.Close, c.surfaceHigh, c.text, onCancel)
        DottedWave(Modifier.weight(1f).height(46.dp).padding(horizontal = 10.dp), active = true)
        RoundIcon(Icons.Rounded.Stop, c.surfaceHigh, c.text, onStop, iconSize = 18.dp)
        Spacer(Modifier.width(10.dp))
        RoundIcon(Icons.Rounded.ArrowUpward, c.text, c.bg, onSend)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, bg: Color, fg: Color, onClick: () -> Unit, iconSize: androidx.compose.ui.unit.Dp = 26.dp) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun InputArea(
    vm: AppViewModel, text: String, onText: (String) -> Unit, showPickers: Boolean,
    onRecording: (Boolean) -> Unit, onModelClick: () -> Unit, onSend: (String) -> Unit,
) {
    val c = Oc.c
    val canSend = text.isNotBlank()
    val field: @Composable (Modifier) -> Unit = { m ->
        BasicTextField(
            value = text, onValueChange = onText, modifier = m,
            textStyle = TextStyle(color = c.text, fontSize = 19.sp),
            cursorBrush = SolidColor(c.text), maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = if (vm.returnKeySends) ImeAction.Send else ImeAction.Default),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend(text) }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Message", color = c.textDim, fontSize = 19.sp)
                    inner()
                }
            }
        )
    }
    val mic = @Composable {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(enabled = vm.isVoiceSupported) {
                if (vm.isVoiceSupported) onRecording(true)
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Mic, null,
                tint = if (vm.isVoiceSupported) c.textDim else c.textDim.copy(alpha = 0.35f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
    if (showPickers) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            field(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 4.dp, top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlusButton()
                Spacer(Modifier.width(10.dp))
                if (vm.agentSelection) { AgentPicker(vm); Spacer(Modifier.width(18.dp)) }
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onModelClick).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(vm.model.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textDim, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                mic()
                Spacer(Modifier.width(8.dp))
                SendButton(vm.working, canSend, { onSend(text) }, vm::stop)
            }
        }
    } else {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlusButton()
            field(Modifier.weight(1f).padding(horizontal = 14.dp))
            mic()
            Spacer(Modifier.width(8.dp))
            SendButton(vm.working, canSend, { onSend(text) }, vm::stop)
        }
    }
}

@Composable
private fun PlusButton() {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = c.text, modifier = Modifier.size(30.dp))
        }
        DropdownMenu(open, { open = false }, containerColor = c.surfaceHigh, shape = RoundedCornerShape(20.dp)) {
            listOf("Photo" to Icons.Rounded.Image, "File" to Icons.Rounded.AttachFile).forEach { (l, i) ->
                DropdownMenuItem(
                    text = { Text(l, color = c.text, fontSize = 17.sp) },
                    leadingIcon = { Icon(i, null, tint = c.textDim) },
                    onClick = { open = false }
                )
            }
        }
    }
}

@Composable
private fun AgentPicker(vm: AppViewModel) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(vm.agent, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textDim, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, { open = false }, containerColor = c.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(min = 230.dp)) {
            vm.agents.forEach {
                DropdownMenuItem(
                    text = { Text(it, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Medium) },
                    trailingIcon = { if (it == vm.agent) Icon(Icons.Rounded.Check, null, tint = c.text) },
                    onClick = { vm.agent = it; open = false }
                )
            }
        }
    }
}

@Composable
private fun SendButton(working: Boolean, canSend: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val c = Oc.c
    val active = working || canSend
    val bg by animateColorAsState(if (active) Color(0xFFEDEDED) else c.surfaceHigh, tween(200), label = "sb")
    val fg by animateColorAsState(if (active) Color(0xFF161616) else c.textDim, tween(200), label = "sf")
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable { if (working) onStop() else if (canSend) onSend() },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(working, label = "send") { w ->
            if (w) Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(fg))
            else Icon(Icons.Rounded.ArrowUpward, null, tint = fg, modifier = Modifier.size(30.dp))
        }
    }
}
