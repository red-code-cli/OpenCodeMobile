package com.opencode.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.opencode.mobile.data.ConnectionState
import com.opencode.mobile.data.ServerProfile
import com.opencode.mobile.ui.theme.Oc

@Composable
fun ServersScreen(vm: AppViewModel) {
    val c = Oc.c
    val profiles = remember(vm.activeServer) { vm.repo.serverStore.getProfiles() }
    var addingServer by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Box(
            Modifier.padding(start = 16.dp, top = 14.dp).size(48.dp).clip(CircleShape).clickable(onClick = vm::pop),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text, modifier = Modifier.size(30.dp))
        }
        Text(
            "Servers",
            color = c.text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 26.dp)
        )

        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("Active Server", color = c.textDim, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))

            val active = vm.activeServer
            if (active != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh).padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when (vm.connectionState) {
                            ConnectionState.CONNECTED -> c.success
                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color(0xFFFF9500)
                            ConnectionState.OFFLINE -> c.danger
                        }
                        Box(Modifier.size(12.dp).background(dotColor, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(active.name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            when (vm.connectionState) {
                                ConnectionState.CONNECTED -> "Online"
                                ConnectionState.CONNECTING -> "Connecting..."
                                ConnectionState.RECONNECTING -> "Reconnecting..."
                                ConnectionState.OFFLINE -> "Offline"
                            },
                            color = dotColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(active.activeUrl ?: active.urls.firstOrNull() ?: "", color = c.textDim, fontSize = 16.sp)
                }
            } else {
                Text("No server connected", color = c.textDim, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
            }

            Spacer(Modifier.height(26.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Saved Profiles (${profiles.size})", color = c.textDim, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { addingServer = !addingServer }) {
                    Icon(if (addingServer) Icons.Rounded.Close else Icons.Rounded.Add, null, tint = c.accent)
                    Spacer(Modifier.width(6.dp))
                    Text(if (addingServer) "Cancel" else "Add Server", color = c.accent, fontSize = 16.sp)
                }
            }

            if (addingServer) {
                ServerSetupCard(vm)
                Spacer(Modifier.height(16.dp))
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh)) {
                profiles.forEachIndexed { index, profile ->
                    val isSel = profile.id == vm.activeServer?.id
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.switchServer(profile) }.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, color = c.text, fontSize = 18.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                            Text(profile.urls.firstOrNull() ?: "", color = c.textDim, fontSize = 14.sp)
                        }
                        if (isSel) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = c.accent, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                        }
                        IconButton(onClick = { vm.removeServer(profile.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (index < profiles.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = c.divider)
                    }
                }
            }
        }
    }
}
