package com.example.projet_android.ui.guide

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.projet_android.R
import com.example.projet_android.ui.navigation.NavigationExtras

class GuideActivity : FragmentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingAudio = false

    private lateinit var titleText: TextView
    private lateinit var addressText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var playAudioButton: Button
    private lateinit var closeButton: Button

    private var audioResName: String? = null

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
        audioResName = intent.getStringExtra(NavigationExtras.EXTRA_POI_AUDIO)

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

        val audioName = audioResName ?: return
        val resourceId = resources.getIdentifier(audioName, "raw", packageName)

        if (resourceId == 0) {
            Toast.makeText(this, R.string.guide_audio_missing, Toast.LENGTH_LONG).show()
            return
        }

        releaseAudio()
        mediaPlayer = MediaPlayer.create(this, resourceId)?.apply {
            setOnCompletionListener {
                pauseAudioGuide()
                seekTo(0)
            }
            start()
        } ?: run {
            Toast.makeText(this, R.string.guide_audio_error, Toast.LENGTH_LONG).show()
            null
        }
        isPlayingAudio = mediaPlayer?.isPlaying == true
        updateAudioButtonState(isPlaying = isPlayingAudio)
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
}
