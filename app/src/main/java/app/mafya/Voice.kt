package app.mafya

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Голос: распознавание речи (микрофон) и озвучка ответов (TTS), оба средствами Android. */
class Voice(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onSpeaking: (Boolean) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var rec: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onSpeaking(true)
                    override fun onDone(utteranceId: String?) = onSpeaking(false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onSpeaking(false)
                })
            }
        }
    }

    fun speak(text: String) {
        val clean = text.replace(Regex("[*_`#>~]"), "").trim()
        if (clean.isNotEmpty()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "mafya")
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeaking(false)
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onListening(false)
            return
        }
        stopSpeaking()
        rec?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) = onListening(false)
            override fun onResults(results: Bundle?) {
                onListening(false)
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) onText(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        r.startListening(i)
    }

    fun stopListening() {
        rec?.stopListening()
        onListening(false)
    }

    fun shutdown() {
        rec?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
