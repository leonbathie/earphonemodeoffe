package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class EarphoneModeService : Service() {

    private lateinit var audioManager: AudioManager
    private var isSpeakerMode = true
    private val handler = Handler(Looper.getMainLooper())

    private val enforceRunnable = object : Runnable {
        override fun run() {
            if (isSpeakerMode) {
                applySpeakerRouting(true)
                handler.postDelayed(this, 1500)
            }
        }
    }

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    private val headsetPlugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                applySpeakerRouting(isSpeakerMode)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        registerReceiver(headsetPlugReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                    super.onPlaybackConfigChanged(configs)
                    if (isSpeakerMode) {
                        applySpeakerRouting(true)
                    }
                }
            }
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback!!, handler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("EarphoneModePrefs", Context.MODE_PRIVATE)
        isSpeakerMode = prefs.getBoolean("isSpeakerMode", true)

        val notification = createNotification()
        startForeground(1, notification)

        handler.removeCallbacks(enforceRunnable)
        if (isSpeakerMode) {
            applySpeakerRouting(true)
            handler.post(enforceRunnable)
        } else {
            applySpeakerRouting(false)
        }

        return START_STICKY
    }

    private fun applySpeakerRouting(enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("")
            }
            if (enable) {
                // Keep MODE_NORMAL so media apps (TikTok, YouTube, Games) play full unsuppressed STREAM_MUSIC audio
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                audioManager.isBluetoothScoOn = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        try {
                            audioManager.setCommunicationDevice(speaker)
                        } catch (e: Exception) {}
                    }
                }

                // Reflection for AudioSystem.setForceUse
                try {
                    val audioSystemClass = Class.forName("android.media.AudioSystem")
                    val setForceUseMethod = audioSystemClass.getMethod(
                        "setForceUse",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    
                    setForceUseMethod.invoke(null, 0, 1) // FOR_COMMUNICATION = 0, FORCE_SPEAKER = 1
                    setForceUseMethod.invoke(null, 1, 1) // FOR_MEDIA = 1, FORCE_SPEAKER = 1
                    setForceUseMethod.invoke(null, 2, 1) // FOR_RECORD = 2, FORCE_SPEAKER = 1
                    setForceUseMethod.invoke(null, 4, 1) // FOR_SYSTEM = 4, FORCE_SPEAKER = 1
                    setForceUseMethod.invoke(null, 7, 1) // FOR_VIBRATE_RINGING = 7, FORCE_SPEAKER = 1
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // OEM Hardware Audio Parameters
                try { audioManager.setParameters("routing=2") } catch (e: Exception) {}
                try { audioManager.setParameters("force_speaker=1") } catch (e: Exception) {}
                try { audioManager.setParameters("speaker=on") } catch (e: Exception) {}
                try { audioManager.setParameters("AudioSetForceToSpeaker=true") } catch (e: Exception) {}

                // Ensure volume is adequate
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currMusic < maxMusic / 3) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxMusic * 0.8).toInt(), 0)
                }
            } else {
                handler.removeCallbacks(enforceRunnable)
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        audioManager.clearCommunicationDevice()
                    } catch (e: Exception) {}
                }

                try {
                    val audioSystemClass = Class.forName("android.media.AudioSystem")
                    val setForceUseMethod = audioSystemClass.getMethod(
                        "setForceUse",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    
                    setForceUseMethod.invoke(null, 0, 0) // FORCE_NONE = 0
                    setForceUseMethod.invoke(null, 1, 0) 
                    setForceUseMethod.invoke(null, 2, 0) 
                    setForceUseMethod.invoke(null, 4, 0) 
                    setForceUseMethod.invoke(null, 7, 0) 
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try { audioManager.setParameters("routing=0") } catch (e: Exception) {}
                try { audioManager.setParameters("force_speaker=0") } catch (e: Exception) {}
                try { audioManager.setParameters("speaker=off") } catch (e: Exception) {}
                try { audioManager.setParameters("AudioSetForceToSpeaker=false") } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(enforceRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback!!)
            } catch (e: Exception) {}
        }
        try {
            unregisterReceiver(headsetPlugReceiver)
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "EarphoneModeChannel",
                "Earphone Mode Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the earphone mode setting active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "EarphoneModeChannel")
            .setContentTitle("Earphone Mode Manager")
            .setContentText("Keeping audio settings active")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

