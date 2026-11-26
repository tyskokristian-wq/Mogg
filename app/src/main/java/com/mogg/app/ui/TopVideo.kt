package com.mogg.app.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Получаем последние ролики MrBeast из открытой RSS-ленты YouTube (ключ не нужен). */
object MrBeastFeed {
    const val CHANNEL_ID = "UCX6OQ3DkcsbYNE6H8uQQuVA"
    private var cache: List<String>? = null

    suspend fun latestVideoIds(): List<String> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        try {
            val conn = URL("https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            // Разбиваем на <entry>, выкидываем Shorts — нужны обычные длинные видео.
            val entries = xml.split("<entry>").drop(1)
            val ids = entries.mapNotNull { e ->
                val id = Regex("<yt:videoId>([^<]+)</yt:videoId>").find(e)?.groupValues?.get(1) ?: return@mapNotNull null
                if (e.contains("/shorts/")) null else id
            }
            val result = ids.ifEmpty { entries.mapNotNull { Regex("<yt:videoId>([^<]+)</yt:videoId>").find(it)?.groupValues?.get(1) } }
            cache = result
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Достаём id из любой ссылки YouTube. */
    fun parseId(link: String): String? {
        val s = link.trim()
        if (s.isEmpty()) return null
        Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})").find(s)?.let { return it.groupValues[1] }
        return if (Regex("^[A-Za-z0-9_-]{11}$").matches(s)) s else null
    }
}

/** Управление громкостью видео сверху (приглушаем, когда говорит голос). */
class TopVideoController {
    internal var player: YouTubePlayer? = null
    var normalVolume = 70
    var duckVolume = 8
    private var ducked = false
    private var paused = false

    fun duck(on: Boolean) {
        ducked = on
        player?.setVolume(if (on) duckVolume else normalVolume)
    }
    fun pause() { paused = true; player?.pause() }
    fun play() { paused = false; player?.play() }
    internal fun applyVolume() { player?.setVolume(if (ducked) duckVolume else normalVolume) }
}

@Composable
fun TopVideo(
    controller: TopVideoController,
    overrideLink: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ids by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(overrideLink) {
        val own = MrBeastFeed.parseId(overrideLink)
        ids = if (own != null) listOf(own) else MrBeastFeed.latestVideoIds()
        if (ids.isNullOrEmpty()) error = "Нет интернета — видео MrBeast не загрузилось. Упражнения работают и так."
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val list = ids
        if (list != null && list.isNotEmpty()) {
            val view = remember {
                YouTubePlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    enableAutomaticInitialization = false
                }
            }
            DisposableEffect(view) {
                lifecycleOwner.lifecycle.addObserver(view)
                var index = 0
                val options = IFramePlayerOptions.Builder(context)
                    .controls(1)
                    .rel(0)
                    .ivLoadPolicy(3)
                    .fullscreen(0)
                    .build()
                view.initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        controller.player = youTubePlayer
                        youTubePlayer.loadVideo(list[0], 0f)
                        controller.applyVolume()
                    }
                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                        if (state == PlayerConstants.PlayerState.PLAYING) controller.applyVolume()
                        if (state == PlayerConstants.PlayerState.ENDED && index + 1 < list.size) {
                            index++; youTubePlayer.loadVideo(list[index], 0f)
                        }
                    }
                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                        // Видео нельзя встроить — берём предыдущее.
                        if (index + 1 < list.size) { index++; youTubePlayer.loadVideo(list[index], 0f) }
                    }
                }, true, options)
                onDispose {
                    controller.player = null
                    lifecycleOwner.lifecycle.removeObserver(view)
                    view.release()
                }
            }
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        } else {
            Text(error ?: "Загружаю последнее видео MrBeast…", color = Color.White.copy(alpha = 0.7f), modifier = Modifier)
        }
    }
}
