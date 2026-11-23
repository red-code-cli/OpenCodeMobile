package com.opencode.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder as FolderOutlined
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.AppViewModel
import com.opencode.mobile.ui.Composer
import com.opencode.mobile.ui.OcTopBar
import com.opencode.mobile.ui.theme.Oc

@Composable
fun HomeScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val c = Oc.c
    var text by rememberSaveable { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        OcTopBar(
            title = if (vm.activeServer != null) "New Session" else "OpenCode",
            subtitle = if (vm.activeServer != null) vm.selectedProject else "Not Connected",
            onMenu = onMenu,
            connectionState = vm.connectionState
        )

        if (vm.activeServer == null) {
            // First open / Unconfigured state: Show Connect Card
            Box(Modifier.weight(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                ServerSetupCard(vm)
            }
        } else {
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                AnimatedContent(
                    targetState = recording,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "projects"
                ) { rec ->
                    Column {
                        if (rec) {
                            ProjectRow(vm.selectedProject, selected = true, dim = false) {}
                        } else {
                            vm.projects.take(4).forEach { p ->
                                ProjectRow(p, p == vm.selectedProject, dim = false) { vm.selectedProject = p }
                            }
                            Row(
                                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).clickable { }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Search, null, tint = c.textDim, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(18.dp))
                                Text("All Projects (${vm.projects.size})", color = c.textDim, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Composer(
                    vm = vm,
                    text = text,
                    onText = { text = it },
                    showPickers = true,
                    recording = recording,
                    onRecording = { recording = it },
                    onModelClick = { showModels = true },
                    onSend = { vm.send(it); text = "" }
                )
            }
        }
    }
    if (showModels) ModelSheet(vm) { showModels = false }
}

@Composable
fun ServerSetupCard(vm: AppViewModel) {
    val c = Oc.c
    var url by rememberSaveable { mutableStateOf("http://127.0.0.1:4096") }
    var username by rememberSaveable { mutableStateOf("opencode") }
    var password by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.surfaceHigh).border(1.dp, c.divider, RoundedCornerShape(26.dp)).padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Dns, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Connect Server", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Enter OpenCode host & credentials", color = c.textDim, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // URL Field
        Text("Server Address (IP or Hostname)", color = c.textDim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Link, null, tint = c.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 17.sp),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (url.isEmpty()) Text("http://192.168.1.100:4096", color = c.textDim, fontSize = 17.sp); inner() }
            )
        }

        Spacer(Modifier.height(14.dp))

        // Username Field
        Text("Username", color = c.textDim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Person, null, tint = c.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 17.sp),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (username.isEmpty()) Text("opencode", color = c.textDim, fontSize = 17.sp); inner() }
            )
        }

        Spacer(Modifier.height(14.dp))

        // Password Field
        Text("Password", color = c.textDim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lock, null, tint = c.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(color = c.text, fontSize = 17.sp),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (password.isEmpty()) Text("Server password", color = c.textDim, fontSize = 17.sp); inner() }
            )
            IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(24.dp)) {
                Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = c.textDim)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Optional Pairing code
        Text("Or Pairing Code (V2)", color = c.textDim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.QrCode, null, tint = c.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = pairingCode,
                onValueChange = { pairingCode = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 17.sp),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (pairingCode.isEmpty()) Text("Paste pairing code from terminal", color = c.textDim, fontSize = 17.sp); inner() }
            )
        }

        if (vm.serverConnectError != null) {
            Spacer(Modifier.height(12.dp))
            Text(vm.serverConnectError ?: "", color = c.danger, fontSize = 15.sp)
        }

        Spacer(Modifier.height(20.dp))

        // Connect button
        Button(
            onClick = {
                vm.connectNewServer(url, username, password, pairingCode.ifBlank { null })
            },
            enabled = !vm.isConnectingServer && (password.isNotBlank() || pairingCode.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White)
        ) {
            if (vm.isConnectingServer) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connecting...", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            } else {
                Text("Connect", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ProjectRow(name: String, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    val bg by animateColorAsState(if (selected) c.surface else Color.Transparent, tween(220), label = "bg")
    val fg by animateColorAsState(if (selected) c.text else c.textDim, tween(220), label = "fg")
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (selected) Icons.Rounded.Folder else Icons.Outlined.FolderOutlined, null, tint = fg, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(18.dp))
        Text(name, color = fg, fontSize = 20.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
