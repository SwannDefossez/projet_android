package com.example.projet_android.ui.guide

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.projet_android.R
import com.example.projet_android.ui.navigation.NavigationExtras
import java.io.File

class GuideActivity : FragmentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingAudio = false
    private var cachedAudioFile: File? = null

    private lateinit var titleText: TextView
    private lateinit var addressText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var playAudioButton: Button
    private lateinit var closeButton: Button

    private var audioAssetName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        titleText = findViewById(R.id.guideTitleText)
        addressText = findViewById(R.id.guideAddressText)
        descriptionText = findViewById(R.id.guideDescriptionText)
        playAudioButton = findViewById(R.id.guidePlayAudioButton)
        closeButton = findViewById(R.id.guideCloseButton)

        val poiName = intent.getStringExtra(NavigationExtras.EXTRA_POI_NAME) ?: getString(R.string.guide_default_title)
        val poiAddress = intent.getStringExtra(NavigationExtras.EXTRA_POI_ADDRESS)
            ?: getString(R.string.guide_default_address)
        val poiDescription = intent.getStringExtra(NavigationExtras.EXTRA_POI_DESC)
            ?: getString(R.string.guide_default_description)
        audioAssetName = intent.getStringExtra(NavigationExtras.EXTRA_POI_AUDIO)

        titleText.text = poiName
        addressText.text = getString(R.string.guide_address, poiAddress)
        descriptionText.text = poiDescription

        updateAudioButtonState(isPlaying = false)
        playAudioButton.setOnClickListener {
            if (isPlayingAudio) {
                pauseAudioGuide()
            } else {
                playAudioGuide()
            }
        }

        closeButton.setOnClickListener { finish() }
    }

    override fun onStop() {
        super.onStop()
        pauseAudioGuide()
    }

    override fun onDestroy() {
        releaseAudio()
        super.onDestroy()
    }

    private fun playAudioGuide() {
        mediaPlayer?.let { existingPlayer ->
            existingPlayer.start()
            isPlayingAudio = true
            updateAudioButtonState(isPlaying = true)
            return
        }

        val audioName = audioAssetName ?: return
        val assetPath = resolveAudioAssetPath(audioName)
        if (assetPath == null) {
            Toast.makeText(this, R.string.guide_audio_missing, Toast.LENGTH_LONG).show()
            return
        }

        releaseAudio()
        val player = MediaPlayer()
        val prepared = prepareMediaPlayerFromAsset(player, assetPath)
        if (!prepared) {
            player.release()
            Toast.makeText(this, R.string.guide_audio_error, Toast.LENGTH_LONG).show()
            return
        }

        mediaPlayer = player.apply {
            setOnCompletionListener {
                pauseAudioGuide()
                seekTo(0)
            }
            start()
        }
        isPlayingAudio = mediaPlayer?.isPlaying == true
        updateAudioButtonState(isPlaying = isPlayingAudio)
    }

    private fun prepareMediaPlayerFromAsset(player: MediaPlayer, assetPath: String): Boolean {
        val directResult = runCatching {
            assets.openFd(assetPath).use { assetFd ->
                player.setDataSource(assetFd.fileDescriptor, assetFd.startOffset, assetFd.length)
            }
            player.prepare()
        }
        if (directResult.isSuccess) return true

        return runCatching {
            val tempFile = File(cacheDir, "guide_audio_${System.currentTimeMillis()}.tmp")
            assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cachedAudioFile = tempFile
            player.setDataSource(tempFile.absolutePath)
            player.prepare()
        }.isSuccess
    }

    private fun resolveAudioAssetPath(audioName: String): String? {
        val trimmed = audioName.trim()
        if (trimmed.isBlank()) return null

        val fileCandidates = if (trimmed.contains('.')) {
            listOf(trimmed)
        } else {
            AUDIO_EXTENSIONS.map { extension -> "$trimmed$extension" }
        }

        val pathCandidates = linkedSetOf<String>()
        for (candidate in fileCandidates) {
            if (candidate.startsWith("$AUDIO_ASSET_DIR/")) {
                pathCandidates.add(candidate)
            } else {
                pathCandidates.add("$AUDIO_ASSET_DIR/$candidate")
                pathCandidates.add(candidate)
            }
        }

        return pathCandidates.firstOrNull { path ->
            runCatching {
                assets.open(path).use { }
            }.isSuccess
        }
    }

    private fun pauseAudioGuide() {
        mediaPlayer?.pause()
        isPlayingAudio = false
        updateAudioButtonState(isPlaying = false)
    }

    private fun releaseAudio() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        cachedAudioFile?.delete()
        cachedAudioFile = null
        isPlayingAudio = false
        updateAudioButtonState(isPlaying = false)
    }

    private fun updateAudioButtonState(isPlaying: Boolean) {
        val textRes = if (isPlaying) R.string.guide_pause_audio else R.string.guide_play_audio
        val iconRes = if (isPlaying) R.drawable.ic_pause_line else R.drawable.ic_play_line
        playAudioButton.text = getString(textRes)
        playAudioButton.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        playAudioButton.compoundDrawablePadding = 10
    }

    companion object {
        private const val AUDIO_ASSET_DIR = "reference_audio"
        private val AUDIO_EXTENSIONS = listOf(".mp3", ".wav", ".ogg")
    }
}
