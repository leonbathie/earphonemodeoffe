package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private var muteOnDisconnect by mutableStateOf(true)
    private var isSpeakerMode by mutableStateOf(true) // Default to speaker mode
    private var mediaPlayer: android.media.MediaPlayer? = null

    // Registered during construction, as required by registerForActivityResult.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Without notification access the service keeps running, but its status notification stays hidden.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val headsetPlugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                // When system detects headset, force our setting again
                forceSpeaker(isSpeakerMode)
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (muteOnDisconnect) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPreferences = getSharedPreferences("EarphoneModePrefs", Context.MODE_PRIVATE)
        
        // Load persisted settings.
        // muteOnDisconnect est desactive par defaut : sur un telephone dont la detection de
        // casque est defaillante, ACTION_AUDIO_BECOMING_NOISY part en rafale et cette option
        // remet le volume media a zero a chaque fois, ce qui rend le telephone inutilisable.
        muteOnDisconnect = sharedPreferences.getBoolean("muteOnDisconnect", false)
        isSpeakerMode = sharedPreferences.getBoolean("isSpeakerMode", true)
        
        // Apply immediately on startup
        requestNotificationPermissionIfNeeded()
        forceSpeaker(isSpeakerMode)
        startEarphoneModeService(isSpeakerMode)
        
        // Register receiver for when earphones are unplugged
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        registerReceiver(headsetPlugReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Earphone Mode Off") },
                            actions = {
                                IconButton(onClick = { /* TODO: Settings Menu */ }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                ) { innerPadding ->
                    EarphoneModeScreen(
                        modifier = Modifier.padding(innerPadding),
                        isSpeakerMode = isSpeakerMode,
                        muteOnDisconnect = muteOnDisconnect,
                        onSpeakerModeChanged = { enabled ->
                            isSpeakerMode = enabled
                            sharedPreferences.edit().putBoolean("isSpeakerMode", enabled).apply()
                            forceSpeaker(enabled)
                            if (enabled) {
                                startEarphoneModeService(true)
                            } else {
                                val serviceIntent = Intent(this@MainActivity, EarphoneModeService::class.java)
                                stopService(serviceIntent)
                            }
                        },
                        onMuteSettingChanged = { enabled -> 
                            muteOnDisconnect = enabled 
                            sharedPreferences.edit().putBoolean("muteOnDisconnect", enabled).apply()
                        },
                        onTestAudio = {
                            testAudio()
                        },
                        onResetAudio = {
                            resetAudio()
                        }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startEarphoneModeService(enabled: Boolean) {
        if (enabled) {
            val serviceIntent = Intent(this, EarphoneModeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun testAudio() {
        try {
            Toast.makeText(this, "Test en STREAM_MUSIC, comme TikTok...", Toast.LENGTH_SHORT).show()

            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)

            RoutingDiagnostics.applyForceUse(true)

            // 1. Sinusoide synthetisee en USAGE_MEDIA / CONTENT_TYPE_MUSIC.
            // C'est volontairement le meme chemin audio que TikTok ou YouTube : si ce son
            // sort du haut-parleur, le forcage marche vraiment. L'ancien test utilisait
            // USAGE_ALARM, que beaucoup de constructeurs routent vers le haut-parleur
            // meme casque branche : il reussissait donc sans rien prouver.
            Thread {
                try {
                    val sampleRate = 44100
                    val durationSeconds = 1.2
                    val numSamples = (durationSeconds * sampleRate).toInt()
                    val sample = DoubleArray(numSamples)
                    val generatedSnd = ByteArray(2 * numSamples)

                    for (i in 0 until numSamples) {
                        sample[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (sampleRate / 880.0))
                    }

                    var idx = 0
                    for (dVal in sample) {
                        val val16 = (dVal * 32767).toInt().toShort()
                        generatedSnd[idx++] = (val16.toInt() and 0x00ff).toByte()
                        generatedSnd[idx++] = (val16.toInt() and 0xff00 shr 8).toByte()
                    }

                    val audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(generatedSnd.size)
                        .build()

                    audioTrack.write(generatedSnd, 0, generatedSnd.size)
                    audioTrack.play()
                    Thread.sleep(1200)
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()

            // 2. Play Ringtone Sound (deja en USAGE_MEDIA plus bas)
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                
            if (uri != null) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
                    
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                        
                    setAudioAttributes(attributes)
                    isLooping = false
                    prepare()
                    start()
                }
                
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                    } catch (e: Exception) {}
                    mediaPlayer = null
                }, 2500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("")
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = false
            
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
                setForceUseMethod.invoke(null, 0, 0)
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

            // Tout arreter, sinon le service reapplique le forcage dans la seconde et
            // BootReceiver le relance au prochain demarrage.
            isSpeakerMode = false
            muteOnDisconnect = false
            sharedPreferences.edit()
                .putBoolean("isSpeakerMode", false)
                .putBoolean("muteOnDisconnect", false)
                .apply()
            stopService(Intent(this, EarphoneModeService::class.java))

            // Remonter le volume media systematiquement : la coupure automatique a pu le
            // laisser a zero, et c'est precisement le symptome qu'on vient reparer.
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (maxVol * 0.7).toInt(),
                AudioManager.FLAG_SHOW_UI
            )
            Toast.makeText(
                this,
                "Routage par défaut restauré, service arrêté, volume rétabli.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun forceSpeaker(enable: Boolean) {
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
                
                RoutingDiagnostics.applyForceUse(true)

                // Commandes brutes au pilote audio du constructeur. Non documentees et non
                // validees : contrairement a setForceUse, elles passent vraiment, donc elles
                // peuvent coincer le routage. Si le systeme a deja refuse l'API documentee,
                // elles n'ont aucune chance d'aboutir proprement et on s'abstient.
                if (!RoutingDiagnostics.forcingRefused) {
                    try { audioManager.setParameters("routing=2") } catch (e: Exception) {}
                    try { audioManager.setParameters("force_speaker=1") } catch (e: Exception) {}
                    try { audioManager.setParameters("speaker=on") } catch (e: Exception) {}
                    try { audioManager.setParameters("AudioSetForceToSpeaker=true") } catch (e: Exception) {}
                }

                // Maximize volume streams
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val currMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currMusic < maxMusic / 3) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxMusic * 0.8).toInt(), 0)
                }
            } else {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        audioManager.clearCommunicationDevice()
                    } catch (e: Exception) {}
                }
                
                RoutingDiagnostics.applyForceUse(false)

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
        unregisterReceiver(noisyReceiver)
        unregisterReceiver(headsetPlugReceiver)
        mediaPlayer?.release()
    }
}

@Composable
fun EarphoneModeScreen(
    modifier: Modifier = Modifier,
    isSpeakerMode: Boolean,
    muteOnDisconnect: Boolean,
    onSpeakerModeChanged: (Boolean) -> Unit,
    onMuteSettingChanged: (Boolean) -> Unit,
    onTestAudio: () -> Unit,
    onResetAudio: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top)
    ) {
        // Le forcage a-t-il ete refuse par le systeme ? Tant qu'on est en mode ecouteurs,
        // la question ne se pose pas.
        val forcingRefused = isSpeakerMode && RoutingDiagnostics.forcingRefused

        // Status Graphic
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    when {
                        forcingRefused -> MaterialTheme.colorScheme.errorContainer
                        isSpeakerMode -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSpeakerMode) Icons.Default.Speaker else Icons.Default.Headphones,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = when {
                    forcingRefused -> MaterialTheme.colorScheme.onErrorContainer
                    isSpeakerMode -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }

        Text(
            text = when {
                forcingRefused -> "Forçage refusé par Android"
                isSpeakerMode -> "Haut-parleur forcé"
                else -> "Mode écouteurs"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = when {
                forcingRefused ->
                    "Ce téléphone n'autorise pas une application ordinaire à rediriger le son " +
                    "des autres applications. Le son de TikTok ou YouTube continuera de suivre " +
                    "le casque détecté."
                isSpeakerMode ->
                    "Le son est redirigé vers le haut-parleur même si un casque est détecté."
                else ->
                    "Routage audio par défaut : le son suit le casque quand il est détecté."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Quand le forcage est refuse, on ne laisse pas l'utilisateur sans solution :
        // le selecteur de sortie d'Android, lui, fonctionne sans permission particuliere.
        if (forcingRefused) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Ce qui marche à la place",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Lancez la vidéo, déroulez le volet de notifications, touchez " +
                                   "l'icône de sortie audio du panneau média, puis choisissez " +
                                   "« Haut-parleur du téléphone ».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Si aucun casque n'est branché alors que le diagnostic indique " +
                                   "WiredHeadset = true, la prise est probablement encrassée : " +
                                   "nettoyez-la, téléphone éteint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Main Toggle Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModeButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speaker,
                title = "Force Speaker",
                isSelected = isSpeakerMode,
                onClick = { onSpeakerModeChanged(true) }
            )
            ModeButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Headphones,
                title = "Use Earphones",
                isSelected = !isSpeakerMode,
                onClick = { onSpeakerModeChanged(false) }
            )
        }

        // Action Buttons Row (Test Audio & Reset Audio)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTestAudio,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Test Audio")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Sound", style = MaterialTheme.typography.titleSmall)
            }

            OutlinedButton(
                onClick = onResetAudio,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Sound")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restore Sound", style = MaterialTheme.typography.titleSmall)
            }
        }

        // Auto Mute Setting Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Auto-Mute on Disconnect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Disable sound automatically when earphones are unplugged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = muteOnDisconnect,
                    onCheckedChange = onMuteSettingChanged,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        // Diagnostic Log Card
        val context = LocalContext.current
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        var diagnosticText by remember { mutableStateOf("Loading...") }
        
        LaunchedEffect(Unit) {
            while (true) {
                @Suppress("DEPRECATION")
                val isWired = audioManager.isWiredHeadsetOn
                @Suppress("DEPRECATION")
                val isSpeakerOn = audioManager.isSpeakerphoneOn
                val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val mode = when (audioManager.mode) {
                    AudioManager.MODE_NORMAL -> "NORMAL"
                    AudioManager.MODE_RINGTONE -> "RINGTONE"
                    AudioManager.MODE_IN_CALL -> "IN_CALL"
                    AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
                    else -> "UNKNOWN (${audioManager.mode})"
                }
                
                var commDeviceStr = "N/A"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val commDev = audioManager.communicationDevice
                    commDeviceStr = commDev?.type?.let { type ->
                        when(type) {
                            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
                            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
                            else -> "TYPE_$type"
                        }
                    } ?: "None"
                }

                diagnosticText = "Audio Mode: $mode\n" +
                                 "SpeakerphoneOn: $isSpeakerOn\n" +
                                 "WiredHeadset Detected: $isWired\n" +
                                 "CommDevice: $commDeviceStr\n" +
                                 "Media Volume: $musicVol / $maxMusicVol\n" +
                                 "setForceUse: ${RoutingDiagnostics.forceUse}"
                
                kotlinx.coroutines.delay(1000)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Live Audio Diagnostic", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = diagnosticText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ModeButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

