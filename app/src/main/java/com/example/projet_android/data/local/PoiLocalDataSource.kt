package com.example.projet_android.data.local

import com.example.projet_android.domain.model.Poi

interface PoiLocalDataSource {
    fun loadPois(): List<Poi>
}
