package com.mogg.app.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Голос, который объясняет упражнение.
 * Сначала ищет готовую озвучку ElevenLabs в assets/voice/<ключ>.mp3,
 * если её нет — говорит встроенным русским голосом Android.
 * Пока голос звучит, видео сверху приглушается (onSpeakingChanged).
 */
class Narrator(context: Context, private val onSpeakingChanged: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null
    private val voiceFiles: Set<String> = try {
        app.assets.list("voice")?.toSet() ?: emptySet()
    } catch (e: Exception) { emptySet() }

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                tts?.setSpeechRate(1.05f)
                ttsReady = true
                pending?.let { speakTts(it) }
                pending = null
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { onSpeakingChanged(true) } }
            override fun onDone(utteranceId: String?) { main.post { onSpeakingChanged(false) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { onSpeakingChanged(false) } }
        })
    }

    fun hasRecordedVoice(key: String) = "$key.mp3" in voiceFiles

    fun speak(key: String, text: String) {
        stop()
        if (hasRecordedVoice(key)) {
            try {
                val afd = app.assets.openFd("voice/$key.mp3")
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.setOnCompletionListener { onSpeakingChanged(false); it.release(); if (player === it) player = null }
                mp.setOnPreparedListener { onSpeakingChanged(true); it.start() }
                mp.prepareAsync()
                player = mp
                return
            } catch (e: Exception) {
                // упадём на TTS
            }
        }
        if (ttsReady) speakTts(text) else pending = text
    }

    private fun speakTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mogg-${System.nanoTime()}")
    }

    fun stop() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        tts?.stop()
        pending = null
        onSpeakingChanged(false)
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
