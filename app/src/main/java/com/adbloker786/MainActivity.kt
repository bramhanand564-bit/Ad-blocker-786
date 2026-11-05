package com.adbloker786

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF151B26)
private val Slate = Color(0xFF586579)
private val Paper = Color(0xFFF4F6F8)
private val Accent = Color(0xFF315B82)

class MainActivity : ComponentActivity() {
    private val prefs: SharedPreferences by lazy { getSharedPreferences("adbloker786", MODE_PRIVATE) }
    private var protectionActive by mutableStateOf(false)
    private var queryCount by mutableStateOf(0L)
    private var blockedCount by mutableStateOf(0L)

    private val vpnConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startProtectionService()
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestVpnConsent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshStats()
        setContent {
            MaterialTheme {
                AdBlockerScreen(
                    active = protectionActive,
                    queries = queryCount,
                    blocked = blockedCount,
                    preferences = prefs,
                    onEnable = ::requestProtection,
                    onDisable = ::disableProtection,
                    onAddBlock = { addRule("custom_blocks", it) },
                    onAddAllow = { addRule("allow_rules", it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        protectionActive = prefs.getBoolean("enabled", false)
        queryCount = prefs.getLong("query_count", 0L)
        blockedCount = prefs.getLong("blocked_count", 0L)
    }

    private fun requestProtection() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else requestVpnConsent()
    }

    private fun requestVpnConsent() {
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) vpnConsentLauncher.launch(consentIntent) else startProtectionService()
    }

    private fun startProtectionService() {
        val intent = Intent(this, AdBlockVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        protectionActive = true
    }

    private fun disableProtection() {
        stopService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
        prefs.edit().putBoolean("enabled", false).apply()
        protectionActive = false
    }

    private fun addRule(key: String, input: String) {
        val domain = FilterEngine.normalize(input) ?: return
        val existing = prefs.getString(key, "").orEmpty()
            .split(Regex("[\\r\\n,;\\s]+"))
            .filter { it.isNotBlank() }
            .toMutableSet()
        existing.add(domain)
        prefs.edit().putString(key, existing.joinToString("\n")).apply()
    }
}

@Composable
private fun AdBlockerScreen(
    active: Boolean,
    queries: Long,
    blocked: Long,
    preferences: SharedPreferences,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onAddBlock: (String) -> Unit,
    onAddAllow: (String) -> Unit
) {
    var mode by remember { mutableStateOf(preferences.getString("mode", "advanced") ?: "advanced") }
    var blockInput by remember { mutableStateOf("") }
    var allowInput by remember { mutableStateOf("") }
    val customBlocks = preferences.getString("custom_blocks", "").orEmpty()
    val allowRules = preferences.getString("allow_rules", "").orEmpty()

    Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("AD BLOKER 786", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Private DNS protection", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Local filtering. No account. No analytics SDK.", color = Slate, fontSize = 14.sp)

            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(if (active) "Protection is on" else "Protection is off", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text("DNS ad and tracker filtering", color = Slate, fontSize = 13.sp)
                        }
                        Text(if (active) "ON" else "OFF", color = if (active) Color(0xFF18794E) else Slate, fontWeight = FontWeight.Bold)
                    }
                    if (active) {
                        OutlinedButton(onClick = onDisable, modifier = Modifier.fillMaxWidth()) { Text("Turn protection off") }
                    } else {
                        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                            Text("Enable protection")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("DNS requests", queries.toString(), Modifier.weight(1f))
                StatCard("Blocked", blocked.toString(), Modifier.weight(1f))
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Filtering strength", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("normal", "advanced", "maximum").forEach { option ->
                            FilterChip(
                                selected = mode == option,
                                onClick = { mode = option; preferences.edit().putString("mode", option).apply() },
                                label = { Text(option.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text("Maximum mode uses broader domain rules. More blocking can break some site features.", color = Slate, fontSize = 12.sp)
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Custom domain rules", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = blockInput, onValueChange = { blockInput = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Block domain (example.com)") })
                    Button(onClick = { onAddBlock(blockInput); blockInput = "" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Add to block list")
                    }
                    OutlinedTextField(value = allowInput, onValueChange = { allowInput = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Always allow domain") })
                    OutlinedButton(onClick = { onAddAllow(allowInput); allowInput = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add to allow list")
                    }
                    Text("Custom blocks: ${customBlocks.lineSequence().filter { it.isNotBlank() }.count()}  •  Allowed: ${allowRules.lineSequence().filter { it.isNotBlank() }.count()}", color = Slate, fontSize = 12.sp)
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What this protection can do", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Filters DNS requests against built-in and your custom domain rules.\n• DNS forwarding uses Cloudflare DNS (1.1.1.1).\n• Normal device traffic is not routed into the VPN.", color = Slate, fontSize = 13.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Limits", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("It cannot reliably remove first-party/server-side ads, YouTube in-stream ads, hard-coded IP traffic or apps using their own encrypted DNS. Android Private DNS may bypass this filter.", color = Slate, fontSize = 12.sp)
                    Text("No visual detection • No Accessibility service • No auto-tap", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Slate, fontSize = 12.sp)
        }
    }
}
