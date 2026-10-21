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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// Палитра: тёмный фон, пять акцентов, у каждого своя роль
private val Teal = Color(0xFF3CC9B4)   // действие, «онлайн»
private val Amber = Color(0xFFFFB454)  // дневник, раздумье
private val Sky = Color(0xFF5CC8FF)    // память
private val Pink = Color(0xFFFF7AB8)   // запись голоса
private val Lilac = Color(0xFFB9A8FF)  // сон
private val PanelBg = Color(0x17FFFFFF)  // полупрозрачные карточки
private val Dim = Color(0xFF9AA3AF)

enum class Screen { MAIN, MEMORY, DIARY, SETTINGS }

class MafyaState(private val ctx: Context) {
    val store = Store(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resetJob: Job? = null

    var emotion by mutableStateOf(Emotion.NEUTRAL)
    var caption by mutableStateOf("О, привет. Это снова я.")
    var userLine by mutableStateOf("")
    var listening by mutableStateOf(false)
    var speaking by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var sleeping by mutableStateOf(false)
    var screen by mutableStateOf(Screen.MAIN)

    val status: String
        get() = when {
            sleeping -> "спит"
            listening -> "слушаю…"
            busy -> "думаю…"
            speaking -> "говорю"
            else -> "онлайн"
        }

    val statusColor: Color
        get() = when {
            sleeping -> Lilac
            listening -> Pink
            busy -> Amber
            speaking -> Teal
            else -> Dim
        }

    val voice = Voice(
        ctx,
        onText = { send(it) },
        onListening = {
            listening = it
            if (it) emotion = Emotion.LISTENING
            else if (emotion == Emotion.LISTENING && !busy) {
                emotion = Emotion.NEUTRAL
            }
        },
        onSpeaking = { speaking = it }
    )

    private fun calmDown() {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(8000)
            while (speaking) delay(500)
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
        val msg = text.trim()
        if (msg.isEmpty() || busy) return
        if (sleeping) wake()
        userLine = msg
        if (store.apiKey.isBlank()) {
            emotion = Emotion.SURPRISED
            caption = "Открой настройки (шестерёнка) и вставь ключ OpenRouter — без него я не могу думать."
            return
        }
        busy = true
        voice.stopSpeaking()
        emotion = Emotion.THINKING
        caption = "…"
        store.addHistory("user", msg)
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
                // неудачное сообщение не остаётся в истории, иначе при повторе оно задвоится
                store.dropLastUserMessage()
                emotion = Emotion.SURPRISED
                caption = e.message ?: "Что-то пошло не так."
            }
            busy = false
            calmDown()
        }
    }

    fun testConnection(done: (String) -> Unit) {
        scope.launch {
            val r = try {
                withContext(Dispatchers.IO) { Brain.ping(store) }
            } catch (e: Exception) {
                "Не получилось: " + (e.message ?: "неизвестная ошибка")
            }
            done(r)
        }
    }

    fun sleep() {
        voice.stopSpeaking()
        voice.stopListening()
        sleeping = true
        emotion = Emotion.SLEEP
        caption = "Спокойной ночи…"
        val hist = store.history()
        val marker = hist.lastOrNull()?.content?.hashCode()?.toString() ?: ""
        if (store.apiKey.isNotBlank() && hist.isNotEmpty() && marker != store.diaryMarker) {
            scope.launch {
                try {
                    val t = withContext(Dispatchers.IO) { Brain.diaryEntry(store) }
                    val d = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())
                    store.addDiary(d, t)
                    store.diaryMarker = marker
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

    override fun onStop() {
        // в фоне не говорим и не слушаем
        state.voice.stopSpeaking()
        state.voice.stopListening()
        super.onStop()
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

/** Текст проявляется по буквам, как субтитры в анонсе. */
@Composable
fun TypeText(text: String, modifier: Modifier = Modifier) {
    var shown by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        val step = if (text.length > 160) 6 else 2
        var i = 0
        while (i < text.length) {
            i = minOf(text.length, i + step)
            shown = text.substring(0, i)
            delay(24)
        }
    }
    Text(
        shown,
        modifier = modifier,
        color = Color.White,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

@Composable
fun Zzz(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "zzz")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "za")
    Column(modifier.alpha(a), horizontalAlignment = Alignment.Start) {
        Text("Z", color = Lilac, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Z", color = Lilac, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
        Text("z", color = Lilac, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 28.dp))
    }
}

@Composable
fun MainScreen(s: MafyaState, onMic: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val sendNow = {
        val t = input
        input = ""
        s.send(t)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // шапка: имя и состояние слева, кнопки справа
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Мафя", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(s.status, color = s.statusColor, fontSize = 13.sp)
            }
            IconButton({ s.screen = Screen.MEMORY }) { MIcon(Ic.BRAIN, tint = Sky) }
            IconButton({ s.screen = Screen.DIARY }) { MIcon(Ic.BOOK, tint = Amber) }
            IconButton({ if (s.sleeping) s.wake() else s.sleep() }) {
                MIcon(if (s.sleeping) Ic.SUN else Ic.MOON, tint = Lilac)
            }
            IconButton({ s.screen = Screen.SETTINGS }) { MIcon(Ic.GEAR) }
        }

        Spacer(Modifier.weight(0.45f))
        Box(Modifier.fillMaxWidth()) {
            Face(
                emotion = s.emotion,
                speaking = s.speaking,
                listening = s.listening,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { s.poke() }
            )
            if (s.sleeping) Zzz(Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 8.dp))
        }
        Spacer(Modifier.weight(0.35f))

        // что ты сказал последним
        if (s.userLine.isNotEmpty()) {
            Text(
                s.userLine,
                color = Dim,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 190.dp).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            TypeText(s.caption, Modifier.padding(horizontal = 12.dp))
        }
        Spacer(Modifier.height(12.dp))

        // поле ввода: таблетка с микрофоном слева и кнопкой отправки справа
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(PanelBg).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onMic) {
                MIcon(
                    if (s.listening) Ic.STOP else Ic.MIC,
                    tint = if (s.listening) Pink else Color.White
                )
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши Мафе…", color = Dim) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendNow() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Teal
                )
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank() && !s.busy) Teal else Color(0x33FFFFFF))
                    .clickable { sendNow() },
                contentAlignment = Alignment.Center
            ) {
                MIcon(Ic.SEND, tint = if (input.isNotBlank() && !s.busy) Color(0xFF06211C) else Dim, size = 22.dp)
            }
        }
    }
}

@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← Назад", color = Dim) }
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PanelBg).padding(14.dp),
        content = content
    )
}

@Composable
fun Empty(icon: Ic, tint: Color, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MIcon(icon, tint = tint.copy(alpha = 0.6f), size = 56.dp)
        Spacer(Modifier.height(14.dp))
        Text(text, color = Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

private val fmtTime = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

@Composable
fun MemoryScreen(s: MafyaState) {
    var tick by remember { mutableIntStateOf(0) }
    val mems = remember(tick) { s.store.memories() }
    val rems = remember(tick) { s.store.reminders() }
    Page("Память", { s.screen = Screen.MAIN }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rems.isNotEmpty()) {
                item { Text("Напоминания", color = Amber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                items(rems) {
                    Panel {
                        Text(fmtTime.format(Date(it.time)), color = Amber, fontSize = 13.sp)
                        Text(it.text, color = Color.White)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            item { Text("Что я помню о тебе", color = Sky, fontWeight = FontWeight.Bold) }
            if (mems.isEmpty()) {
                item { Empty(Ic.BRAIN, Sky, "Пока ничего. Расскажи о себе, и я запомню важное.") }
            }
            itemsIndexed(mems) { i, m ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m, color = Color.White, modifier = Modifier.weight(1f))
                        IconButton({ s.store.removeMemory(i); tick++ }) { MIcon(Ic.CLOSE, tint = Dim, size = 20.dp) }
                    }
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
            Empty(Ic.BOOK, Amber, "Пусто. Когда я ложусь спать (кнопка с луной), я записываю, как прошёл день.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries) { e ->
                Panel {
                    Text(e.date, color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(e.text, color = Color.White, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(s: MafyaState) {
    var base by remember { mutableStateOf(s.store.apiBase) }
    var key by remember { mutableStateOf(s.store.apiKey) }
    var testResult by remember { mutableStateOf("") }
    var speakOn by remember { mutableStateOf(s.store.speak) }

    Page("Настройки", { s.screen = Screen.MAIN }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel {
                Text("Адрес API", color = Dim, fontSize = 13.sp)
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it; s.store.apiBase = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Ключ OpenRouter", color = Dim, fontSize = 13.sp)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; s.store.apiKey = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Озвучивать ответы", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = speakOn,
                        onCheckedChange = { speakOn = it; s.store.speak = it }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    testResult = "Проверяю…"
                    s.testConnection { testResult = it }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color(0xFF06211C)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Проверить соединение") }
            if (testResult.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(testResult, color = Color.White)
            }
        }
    }
}
