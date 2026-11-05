package com.adbloker786

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Ink=Color(0xFF111827); private val Paper=Color(0xFFF5F6F8); private val Blue=Color(0xFF345B83)
class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); setContent { var active by remember { mutableStateOf(false) }; MaterialTheme { Column(Modifier.fillMaxSize().background(Paper).padding(22.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) { Text("AD BLOKER 786", style=MaterialTheme.typography.labelLarge, color=Blue); Text("Your privacy, protected.", style=MaterialTheme.typography.headlineMedium, color=Ink); Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(22.dp)) { Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(if(active) "Protection requested" else "Protection is off", style=MaterialTheme.typography.titleLarge, color=Ink); Text("Local DNS filtering • No account • No external analytics", color=Color.DarkGray); Button(onClick={ val i=VpnService.prepare(this@MainActivity); if(i!=null) startActivityForResult(i,10) else startService(Intent(this@MainActivity,AdBlockVpnService::class.java)); active=true }, colors=ButtonDefaults.buttonColors(containerColor=Blue), modifier=Modifier.fillMaxWidth()) { Text("Enable protection") }; OutlinedButton(onClick={stopService(Intent(this@MainActivity,AdBlockVpnService::class.java));active=false}, modifier=Modifier.fillMaxWidth()) { Text("Turn off") } } }; Text("Filtering mode", style=MaterialTheme.typography.titleMedium, color=Ink); Text("Maximum mode blocks known DNS ad and tracker domains. Apps using encrypted DNS, hard-coded IPs or first-party ad delivery may bypass filtering.", color=Color.DarkGray); Text("No visual detection • No accessibility service • No auto-tap", color=Blue) } } } } }
