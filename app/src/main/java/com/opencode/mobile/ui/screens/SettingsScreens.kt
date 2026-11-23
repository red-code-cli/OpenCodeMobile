package com.opencode.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.AppConstants
import com.opencode.mobile.data.AppViewModel
import com.opencode.mobile.data.Screen
import com.opencode.mobile.ui.DottedWave
import com.opencode.mobile.ui.OcSwitch
import com.opencode.mobile.ui.theme.Oc
import com.opencode.mobile.ui.theme.ThemeMode

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = Oc.c
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.padding(start = 16.dp, top = 14.dp).size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text, modifier = Modifier.size(30.dp))
        }
        Text(title, color = c.text, fontSize = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 26.dp))
        Column(
            Modifier.weight(1f).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp),
            content = content
        )
    }
}

@Composable
private fun Label(t: String) = Text(t, color = Oc.c.textDim, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp, top = 6.dp))

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp).clip(RoundedCornerShape(24.dp)).background(Oc.c.surfaceHigh), content = content)
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, divider: Boolean = false, onChange: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Oc.c.text, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            OcSwitch(checked, onChange)
        }
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Oc.c.divider)
    }
}

@Composable
fun SettingsHub(vm: AppViewModel) {
    val c = Oc.c
    SettingsPage("Settings", vm::pop) {
        Card {
            listOf(
                Triple(Icons.Rounded.Dns, "Servers", Screen.Servers),
                Triple(Icons.Rounded.Palette, "Appearance", Screen.Appearance),
                Triple(Icons.Rounded.Language, "Language", Screen.Language),
                Triple(Icons.Rounded.Tune, "Sessions", Screen.Sessions),
                Triple(Icons.Rounded.GraphicEq, "Voice", Screen.Voice),
                Triple(Icons.Rounded.Science, "Experimental", Screen.Experimental),
                Triple(Icons.Rounded.NewReleases, "What's New", Screen.WhatsNew),
            ).forEachIndexed { i, (icon, label, scr) ->
                Row(Modifier.fillMaxWidth().clickable { vm.push(scr) }.padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = c.textDim)
                    Spacer(Modifier.width(18.dp))
                    Text(label, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = c.textDim)
                }
                if (i < 6) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = c.divider)
            }
        }
    }
}

@Composable
fun AppearanceScreen(vm: AppViewModel) {
    val c = Oc.c
    var themeOpen by remember { mutableStateOf(false) }
    SettingsPage("Appearance", vm::pop) {
        Label("Mode")
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp).clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.divider, RoundedCornerShape(16.dp))) {
                ThemeMode.entries.forEach { m ->
                    val sel = vm.themeMode == m
                    val bg by animateColorAsState(if (sel) c.bg else Color.Transparent, label = "seg")
                    val fg by animateColorAsState(if (sel) c.text else c.textDim, label = "segf")
                    Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { vm.updateThemeMode(m) }, contentAlignment = Alignment.Center) {
                        Text(m.name, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Card {
            Box {
                Row(Modifier.fillMaxWidth().clickable { themeOpen = true }.padding(horizontal = 24.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Theme", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        listOf(Color(0xFF222222), Color(0xFFDDDDDD), Color(0xFFF58AC4)).forEach { Box(Modifier.size(24.dp).background(it, CircleShape)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(vm.themeName, color = c.textDim, fontSize = 19.sp)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textDim)
                }
                DropdownMenu(themeOpen, { themeOpen = false }, containerColor = c.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.align(Alignment.TopEnd)) {
                    listOf("OpenCode", "Tokyo Night", "Dracula", "Nord", "Gruvbox").forEach {
                        DropdownMenuItem(text = { Text(it, color = c.text, fontSize = 18.sp) }, onClick = { vm.updateThemeName(it); themeOpen = false })
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageScreen(vm: AppViewModel) {
    val c = Oc.c
    var q by remember { mutableStateOf("") }
    SettingsPage("Language", vm::pop, scroll = false) {
        Row(Modifier.fillMaxWidth().height(60.dp).border(1.dp, c.divider, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = c.textDim)
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                q, { q = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 19.sp), cursorBrush = SolidColor(c.text),
                decorationBox = { i -> Box { if (q.isEmpty()) Text("Search Languages", color = c.textDim, fontSize = 19.sp); i() } }
            )
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surfaceHigh).clickable { vm.updateLanguage("system") }.padding(horizontal = 24.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("System Default", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(java.util.Locale.getDefault().let { "${it.getDisplayLanguage(it)} (${it.getDisplayCountry(it)})" }, color = c.textDim, fontSize = 18.sp)
                        }
                        if (vm.language == "system") Icon(Icons.Rounded.Check, null, tint = c.accent)
                    }
                }
                Label2("Suggested")
                LangRow("English", vm, first = true, last = true)
                Label2("All Languages")
            }
            val list = AppConstants.languages.filter { it.contains(q, true) }
            items(list.size) { i -> LangRow(list[i], vm, first = i == 0, last = i == list.lastIndex) }
        }
    }
}

@Composable
private fun Label2(t: String) = Text(t, color = Oc.c.text, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp, top = 30.dp, bottom = 14.dp))

@Composable
private fun LangRow(name: String, vm: AppViewModel, first: Boolean, last: Boolean) {
    val c = Oc.c
    val shape = RoundedCornerShape(topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp, bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surfaceHigh).clickable { vm.updateLanguage(name) }) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (vm.language == name) Icon(Icons.Rounded.Check, null, tint = c.accent)
        }
        if (!last) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = c.divider)
    }
}

@Composable
fun SessionsScreen(vm: AppViewModel) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    SettingsPage("Sessions", vm::pop) {
        Label("Composer")
        Card {
            SwitchRow("Return Key Sends", vm.returnKeySends, divider = false) { vm.updateReturnKeySends(it) }
            SwitchRow("Agent Selection", vm.agentSelection) { vm.updateAgentSelection(it) }
        }
        Label("New Sessions")
        Card {
            Box {
                Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 24.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Default Agent", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(vm.defaultAgent, color = c.textDim, fontSize = 19.sp)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.textDim)
                }
                DropdownMenu(open, { open = false }, containerColor = c.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.align(Alignment.TopEnd)) {
                    (listOf("Server Default") + vm.agents).forEach {
                        DropdownMenuItem(
                            text = { Text(it, color = c.text, fontSize = 18.sp) },
                            onClick = {
                                vm.updateDefaultAgent(it)
                                open = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExperimentalScreen(vm: AppViewModel) {
    val c = Oc.c
    SettingsPage("Experimental", vm::pop) {
        Card { SwitchRow("All Experiments", vm.allExperiments) { vm.setAllExperiments(it) } }
        Text("Also turns on new ones, except any that see your screen or listen in the background.", color = c.textDim, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 22.dp, top = (-14).dp))
        val groups = listOf(
            null to listOf("Hide Unused Models"),
            "Calls" to listOf("Background Call Shortcut", "OpenCode Session Delegator", "Share Screen with Agents"),
            "Drawer" to listOf("Inbox Sidebar", "Session Live Updates", "Sound Effects", "Open Links by Server Address"),
        )
        groups.forEach { (label, keys) ->
            if (label != null) Label(label)
            Card {
                keys.forEachIndexed { i, k ->
                    SwitchRow(k, vm.experiments[k] == true, divider = i < keys.lastIndex) { vm.experiments[k] = it }
                }
            }
        }
    }
}

@Composable
fun VoiceScreen(vm: AppViewModel) {
    val c = Oc.c
    SettingsPage("Voice", vm::pop) {
        DottedWave(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 24.dp), active = vm.voiceEnabled && vm.isVoiceSupported)
        Spacer(Modifier.height(24.dp))
        Card {
            SwitchRow("Enable Voice", vm.voiceEnabled && vm.isVoiceSupported) {
                if (vm.isVoiceSupported) vm.updateVoiceEnabled(it)
            }
        }
        if (!vm.isVoiceSupported) {
            Text("Server voice is not configured or not supported on this server.", color = c.danger, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp, top = (-14).dp, bottom = 22.dp))
        } else {
            Text("Calls use your server's configured provider key.", color = c.textDim, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp, top = (-14).dp, bottom = 22.dp))
        }
        Label("Calls")
        Card {
            Row(Modifier.fillMaxWidth().clickable { }.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text("Call History", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun WhatsNewScreen(vm: AppViewModel) {
    val c = Oc.c
    SettingsPage("What's New", vm::pop) {
        AppConstants.whatsNew.forEach { (date, items) ->
            Text(date, color = c.text, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 14.dp))
            Card {
                items.forEachIndexed { i, (title, body, color) ->
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Color(color)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(22.dp))
                        Column {
                            Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(body, color = c.textDim, fontSize = 18.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    if (i < items.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = c.divider)
                }
            }
        }
    }
}
