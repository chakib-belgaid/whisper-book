package com.whisperbook.app.ui.screens

import com.whisperbook.app.R

/** Keeps the visual identity of an embedded voice stable across every screen. */
internal fun voiceAvatarRes(voiceId: String): Int = when (voiceId) {
    "bella" -> R.drawable.voice_bella
    "jasper" -> R.drawable.voice_jasper
    "luna" -> R.drawable.voice_luna
    "bruno" -> R.drawable.voice_bruno
    "rosie" -> R.drawable.voice_rosie
    "hugo" -> R.drawable.voice_hugo
    "kiki" -> R.drawable.voice_kiki
    "leo" -> R.drawable.voice_leo
    else -> R.drawable.portrait_narrator
}
