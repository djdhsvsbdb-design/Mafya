package app.mafya

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { MAIN, MEMORY, DIARY, SETTINGS }

class MafyaState(private val ctx: Context) {
    val store = Store(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resetJob: Job? = null

    var emotion by mutableStateOf(Emotion.NEUTRAL)
    var caption by mutableStateOf("О, привет. Это снова я.")
    var listening by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var sleeping by mutableStateOf(false)
    var screen by mutableStateOf(Screen.MAIN)

    val voice = Voice(
        ctx,
        onText = { send(it) },
        onListening = { listening = it; if (it) emotion = Emotion.LISTENING },
        onSpeaking = { speaking = it }
    )

    private fun calmDown() {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(8000)
            if (!sleeping && !busy && !listening) emotion = Emotion.NEUTRAL
        }
    }

    fun poke() {
        if (sleeping) { wake(); return }
        emotion = Emotion.WINK
        calmDown()
    }

    fun toggleListen() {
        if (sleeping) wake()
        if (listening) voice.stopListening() else voice.listen()
    }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        if (sleeping) wake()
        if (store.apiKey.isBlank()) {
            emotion = Emotion.SURPRISED
            caption = "Открой ⚙️ и вставь ключ OpenRouter — без него я не могу думать."
            return
        }
        busy = true
        emotion = Emotion.THINKING
        caption = "…"
        store.addHistory("user", text)
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { Brain.chat(store) }
                val r = Brain.parse(raw)
                store.addHistory("assistant", r.text)
                r.memories.forEach { store.addMemory(it) }
                r.reminders.forEach { (t, s) ->
                    store.addReminder(t, s)
                    Reminders.schedule(ctx, t, s)
                }
                emotion = r.emotion
                caption = r.text
                if (store.speak) voice.speak(r.text)
            } catch (e: Exception) {
                emotion = Emotion.SURPRISED
                caption = "Ошибка: ${e.message}"
            }
            busy = false
            calmDown()
        }
    }

    fun sleep() {
        voice.stopSpeaking()
        sleeping = true
        emotion = Emotion.SLEEP
        caption = "Спокойной ночи… zzz"
        if (store.apiKey.isNotBlank() && store.history().isNotEmpty()) {
            scope.launch {
                try {
                    val t = withContext(Dispatchers.IO) { Brain.diaryEntry(store) }
                    val d = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())
                    store.addDiary(d, t)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun wake() {
        sleeping = false
        emotion = Emotion.HAPPY
        caption = "О, привет! Я проснулась."
        calmDown()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var state: MafyaState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = MafyaState(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { MafyaApp(state) }
        }
    }

    override fun onDestroy() {
        state.voice.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MafyaApp(s: MafyaState) {
    val ctx = LocalContext.current
    val glow by animateColorAsState(s.emotion.glow, tween(700), label = "glow")

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) s.toggleListen()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = s.screen != Screen.MAIN) { s.screen = Screen.MAIN }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.55f), Color.Black, Color.Black)))
    ) {
        when (s.screen) {
            Screen.MAIN -> MainScreen(s) {
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) s.toggleListen() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Screen.MEMORY -> MemoryScreen(s)
            Screen.DIARY -> DiaryScreen(s)
            Screen.SETTINGS -> SettingsScreen(s)
        }
    }
}

@Composable
fun MainScreen(s: MafyaState, onMic: () -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton({ s.screen = Screen.MEMORY }) { Text("🧠", fontSize = 22.sp) }
            TextButton({ s.screen = Screen.DIARY }) { Text("📖", fontSize = 22.sp) }
            TextButton({ if (s.sleeping) s.wake() else s.sleep() }) {
                Text(if (s.sleeping) "☀️" else "🌙", fontSize = 22.sp)
            }
            TextButton({ s.screen = Screen.SETTINGS }) { Text("⚙️", fontSize = 22.sp) }
        }
        Spacer(Modifier.weight(0.5f))
        Face(
            emotion = s.emotion,
            speaking = s.speaking,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { s.poke() }
        )
        Spacer(Modifier.weight(0.4f))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 180.dp).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                s.caption,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши Мафе…") },
                singleLine = true
            )
            TextButton(onMic) { Text(if (s.listening) "⏹" else "🎤", fontSize = 24.sp) }
            TextButton({
                val t = input
                input = ""
                s.send(t)
            }) { Text("➤", fontSize = 24.sp) }
        }
    }
}

@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← Назад") }
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

private val fmtTime = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

@Composable
fun MemoryScreen(s: MafyaState) {
    var tick by remember { mutableIntStateOf(0) }
    val mems = remember(tick) { s.store.memories() }
    val rems = remember(tick) { s.store.reminders() }
    Page("Память", { s.screen = Screen.MAIN }) {
        if (rems.isNotEmpty()) {
            Text("Напоминания", color = Color(0xFFFFB454), fontWeight = FontWeight.Bold)
            rems.forEach {
                Text("• ${fmtTime.format(Date(it.time))} — ${it.text}", color = Color.White, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text("Что я помню о тебе", color = Color(0xFF5CC8FF), fontWeight = FontWeight.Bold)
        if (mems.isEmpty()) {
            Text("Пока ничего. Расскажи о себе!", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(mems) { i, m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(m, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton({ s.store.removeMemory(i); tick++ }) { Text("✕", color = Color.Gray) }
                }
            }
        }
    }
}

@Composable
fun DiaryScreen(s: MafyaState) {
    val entries = remember { s.store.diary().reversed() }
    Page("Дневник", { s.screen = Screen.MAIN }) {
        if (entries.isEmpty()) {
            Text(
                "Пусто. Когда я ухожу в режим сна (🌙), я записываю в дневник, как прошёл день.",
                color = Color.Gray
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(entries) { e ->
                Text(e.date, color = Color(0xFFFFB454), fontWeight = FontWeight.Bold)
                Text(e.text, color = Color.White, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(s: MafyaState) {
    var base by remember { mutableStateOf(s.store.apiBase) }
    var key by remember { mutableStateOf(s.store.apiKey) }
    var model by remember { mutableStateOf(s.store.model) }
    var name by remember { mutableStateOf(s.store.userName) }
    var speak by remember { mutableStateOf(s.store.speak) }
    Page("Настройки", { s.screen = Screen.MAIN }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                name, { name = it; s.store.userName = it },
                label = { Text("Как тебя зовут") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                base, { base = it; s.store.apiBase = it },
                label = { Text("API URL (по умолчанию OpenRouter)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                key, { key = it; s.store.apiKey = it },
                label = { Text("API-ключ") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                model, { model = it; s.store.model = it },
                label = { Text("Модель") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Озвучивать ответы", color = Color.White, modifier = Modifier.weight(1f))
                Switch(speak, { speak = it; s.store.speak = it })
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Подойдёт любой сервис с OpenAI-совместимым API: OpenAI, OpenRouter, Groq, DeepSeek и т.д. " +
                    "Ключ хранится только на телефоне.",
                color = Color.Gray, fontSize = 13.sp
            )
        }
    }
}
