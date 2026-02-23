package com.neubofy.watch.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * NotificationListenerService that listens for active media sessions.
 * When a song changes or play state changes, it sends the info to the watch
 * via BleConnectionManager.
 *
 * This requires the user to grant "Notification Access" in system settings.
 */
class MediaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaListener"

        // Singleton reference so NFWatchService can set the connection manager
        var connectionManagerRef: com.neubofy.watch.ble.BleConnectionManager? = null
        
        var instance: MediaListenerService? = null

        fun isNotificationAccessGranted(context: Context): Boolean {
            val componentName = ComponentName(context, MediaListenerService::class.java)
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.contains(componentName.flattenToString())
        }

        fun dispatchMediaAction(action: String): Boolean {
            val controls = instance?.activeController?.transportControls ?: return false
            when (action) {
                "PLAY_PAUSE" -> {
                    if (instance?.lastIsPlaying == true) controls.pause() else controls.play()
                }
                "NEXT" -> controls.skipToNext()
                "PREV" -> controls.skipToPrevious()
                else -> return false
            }
            return true
        }
    }

    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var lastArtist: String? = null
    private var lastTrack: String? = null
    private var lastIsPlaying: Boolean? = null

    private val sessionCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            handleMetadataChange(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            handlePlaybackState(state)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateActiveSession(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected")
        instance = this
        setupSessionListener()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
        if (instance == this) instance = null
        activeController?.unregisterCallback(sessionCallback)
        activeController = null
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        
        // Ignore system, ongoing, or media notifications for standard watch alerts
        if (sbn.isOngoing || pkg == "android" || pkg.contains("systemui") || 
            sbn.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) {
            return
        }

        val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        var text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        if (text.isBlank()) {
            text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        }

        if (title.isNotBlank() || text.isNotBlank()) {
            val displayTitle = if (title.isNotBlank()) title else "Notification"
            
            val appType = when {
                pkg.contains("dialer") || pkg.contains("telecom") -> 0 // Call
                pkg.contains("mms") || pkg.contains("sms") || pkg.contains("messaging") -> 1 // SMS
                pkg.contains("tencent.mm") -> 2 // WeChat
                pkg.contains("tencent.mobileqq") -> 3 // QQ
                pkg.contains("facebook") -> 4 // Facebook
                pkg.contains("twitter") -> 5 // Twitter
                pkg.contains("instagram") -> 6 // Instagram
                pkg.contains("skype") -> 7 // Skype
                pkg.contains("whatsapp") -> 8 // WhatsApp
                pkg.contains("line") -> 9 // Line
                pkg.contains("kakao") -> 10 // KakaoTalk
                else -> 11 // Other
            }
            
            // Send to BleConnectionManager
            connectionManagerRef?.sendNotification(appType, displayTitle, text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not used for forwarding
    }

    private fun setupSessionListener() {
        try {
            sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, MediaListenerService::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)

            // Also check currently active sessions
            val controllers = sessionManager?.getActiveSessions(componentName)
            updateActiveSession(controllers)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup session listener", e)
        }
    }

    private fun updateActiveSession(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            activeController?.unregisterCallback(sessionCallback)
            activeController = null
            return
        }

        // Pick the first active (playing) controller, or just the first one
        val playing = controllers.find {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers[0]

        if (playing != activeController) {
            activeController?.unregisterCallback(sessionCallback)
            activeController = playing
            playing.registerCallback(sessionCallback)

            // Immediately read current metadata and state
            handleMetadataChange(playing.metadata)
            handlePlaybackState(playing.playbackState)
        }
    }

    private fun handleMetadataChange(metadata: MediaMetadata?) {
        if (metadata == null) return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val track = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""

        // Only send if changed (avoid spamming the BLE link)
        if (artist != lastArtist || track != lastTrack) {
            lastArtist = artist
            lastTrack = track
            Log.d(TAG, "Music changed: $artist - $track")
            connectionManagerRef?.sendMusicInfo(artist, track)
        }
    }

    private fun handlePlaybackState(state: PlaybackState?) {
        if (state == null) return
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        if (isPlaying != lastIsPlaying) {
            lastIsPlaying = isPlaying
            Log.d(TAG, "Playback state: ${if (isPlaying) "PLAYING" else "PAUSED"}")
            connectionManagerRef?.sendMusicState(isPlaying)
        }
    }
}
