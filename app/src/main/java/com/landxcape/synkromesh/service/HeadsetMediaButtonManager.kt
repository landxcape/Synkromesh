package com.landxcape.synkromesh.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeadsetMediaButtonManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var mediaSession: MediaSession? = null
    var onButtonClickListener: (() -> Unit)? = null

    fun activateMediaSession() {
        if (mediaSession != null) return

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, context, HeadsetMediaButtonReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val session = MediaSession(context, "SynkromeshMediaSession").apply {
            @Suppress("DEPRECATION")
            setMediaButtonReceiver(pendingIntent)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                onButtonClickListener?.invoke()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onPlay() {
                    onButtonClickListener?.invoke()
                }

                override fun onPause() {
                    onButtonClickListener?.invoke()
                }
            })

            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE
                )
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()

            setPlaybackState(state)
            isActive = true
        }

        mediaSession = session
    }

    fun deactivateMediaSession() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
    }
}
