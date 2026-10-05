package com.lionel.hub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.speech.RecognizerIntent
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun LionelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val TABS = listOf("✨" to "Hub", "🗂️" to "Apps", "🎯" to "Focus", "🔒" to "Lock & AI")

@Composable
fun LionelHub() {
    val context = LocalContext.current
    val store = remember { LionelStore.get(context) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { InstalledApps.load(context) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, (emoji, title) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(emoji) },
                        label = { Text(title, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HubTab(apps, store)
                1 -> AppsTab(apps)
                2 -> FocusTab(apps, store)
                else -> LockAiTab(apps, store)
            }
        }
    }
}

// ------------------------------------------------------------------ Hub

@Composable
private fun HubTab(apps: List<AppEntry>, store: LionelStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(apps) { CommandEngine(context, apps) }
    var input by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val submit: () -> Unit = {
        val text = input.trim()
        if (text.isNotEmpty() && !busy) {
            scope.launch {
                busy = true
                val result = engine.interpret(text)
                reply = result.message
                result.action?.let { engine.execute(it) }
                if (result.action is LionelAction.OpenApp) input = ""
                busy = false
            }
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val spoken = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && spoken != null) {
            input = spoken
            submit()
        }
    }

    val results = remember(input, apps) {
        if (input.isBlank()) emptyList() else Fuzzy.rank(input, apps, 8)
    }
    val suggestions = remember(apps) {
        store.suggestions(6).mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text("Lionel", style = MaterialTheme.typography.headlineMedium)
        }
        item(key = "bar") {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Ask or type: open camera, alarm 6:30 am…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    TextButton(onClick = {
                        try {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    ),
                            )
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No voice input available", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("🎤") }
                },
            )
        }
        item(key = "go") {
            Button(onClick = submit, enabled = !busy && input.isNotBlank()) {
                Text(if (busy) "Thinking…" else "Go")
            }
        }
        if (reply.isNotBlank()) {
            item(key = "reply") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(reply, modifier = Modifier.padding(12.dp))
                }
            }
        }
        if (results.isNotEmpty()) {
            items(results, key = { "r-" + it.component.flattenToString() }) { app ->
                AppRow(app, onClick = { InstalledApps.launch(context, app) })
            }
        } else if (input.isBlank()) {
            item(key = "sugg-title") {
                Text("Suggested for this time", style = MaterialTheme.typography.titleMedium)
            }
            item(key = "sugg") {
                if (suggestions.isEmpty()) {
                    Text(
                        "Use your apps for a few days and Lionel will learn what you open at each hour.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        suggestions.forEach { app ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { InstalledApps.launch(context, app) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(app, 44.dp)
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "ideas") {
                Text(
                    "Try: “open youtube”, “timer 10 min”, “call 017…”, “turn on wifi”, “search weather”.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Apps (auto-organized)

@Composable
private fun AppsTab(apps: List<AppEntry>) {
    val context = LocalContext.current
    val groups = remember(apps) {
        apps.groupBy { it.category }.toSortedMap(compareBy<AppCategory> { it.ordinal })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        groups.forEach { (category, list) ->
            item(key = "h-" + category.name) {
                Text(
                    "${category.emoji}  ${category.title} (${list.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "a-" + it.component.flattenToString() }) { app ->
                AppRow(app, onClick = { InstalledApps.launch(context, app) })
            }
        }
    }
}

// ------------------------------------------------------------------ Focus

@Composable
private fun FocusTab(apps: List<AppEntry>, store: LionelStore) {
    val context = LocalContext.current
    var until by remember { mutableLongStateOf(store.focusUntil) }
    var selected by remember { mutableStateOf(store.focusApps) }
    val on = System.currentTimeMillis() < until

    fun startFocus(millis: Long) {
        val end = if (millis == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + millis
        store.focusUntil = end
        until = end
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "status") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (on) {
                        val text = if (until == Long.MAX_VALUE) {
                            "Focus is ON until you stop it"
                        } else {
                            "Focus is ON until " + android.text.format.DateFormat.getTimeFormat(context).format(Date(until))
                        }
                        Text(text, style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = {
                            store.focusUntil = 0L
                            until = 0L
                        }) { Text("Stop focus") }
                    } else {
                        Text("Focus is off", style = MaterialTheme.typography.titleMedium)
                        Text("Selected apps are blocked while focus is on.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { startFocus(25 * 60_000L) }) { Text("25 min") }
                            FilledTonalButton(onClick = { startFocus(60 * 60_000L) }) { Text("1 h") }
                            FilledTonalButton(onClick = { startFocus(2 * 60 * 60_000L) }) { Text("2 h") }
                        }
                        TextButton(onClick = { startFocus(Long.MAX_VALUE) }) { Text("Until I stop it") }
                    }
                }
            }
        }
        item(key = "pick-title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Distracting apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val auto = setOf(AppCategory.SOCIAL, AppCategory.GAME, AppCategory.VIDEO)
                    selected = selected + apps.filter { it.category in auto }.map { it.packageName }
                    store.focusApps = selected
                }) { Text("Auto-pick") }
            }
        }
        items(apps, key = { "f-" + it.component.flattenToString() }) { app ->
            AppRow(
                app,
                onClick = {
                    selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                    store.focusApps = selected
                },
                trailing = { Checkbox(checked = app.packageName in selected, onCheckedChange = null) },
            )
        }
    }
}

// ------------------------------------------------------------------ Lock + AI

@Composable
private fun LockAiTab(apps: List<AppEntry>, store: LionelStore) {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(store.lockedApps) }
    var key by remember { mutableStateOf(store.apiKey) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "ai") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI command bar", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional. Simple English commands work offline. For other languages (Bengali too) and questions, " +
                            "add an Anthropic API key. Only the text you type is sent. The key is stored on this device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        store.apiKey = key.trim()
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }) { Text("Save") }
                }
            }
        }
        item(key = "lock-title") {
            Column {
                Text("App lock", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Locked apps ask for your screen PIN, pattern or fingerprint when opened from Lionel. " +
                        "Needs a screen lock set on the phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(apps, key = { "l-" + it.component.flattenToString() }) { app ->
            AppRow(
                app,
                onClick = {
                    locked = if (app.packageName in locked) locked - app.packageName else locked + app.packageName
                    store.lockedApps = locked
                },
                trailing = { Checkbox(checked = app.packageName in locked, onCheckedChange = null) },
            )
        }
    }
}

// ------------------------------------------------------------------ shared pieces

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

private val iconCache = LruCache<String, ImageBitmap>(300)

@Composable
private fun AppIcon(app: AppEntry, size: Dp) {
    val context = LocalContext.current
    val key = app.component.flattenToString()
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(iconCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.Default) { loadIcon(context, app) }
            if (loaded != null) {
                iconCache.put(key, loaded)
                bitmap = loaded
            }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}

private fun loadIcon(context: Context, app: AppEntry): ImageBitmap? = try {
    val drawable = context.packageManager.getActivityIcon(app.component)
    val px = 120
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bmp))
    bmp.asImageBitmap()
} catch (e: Exception) {
    null
}
