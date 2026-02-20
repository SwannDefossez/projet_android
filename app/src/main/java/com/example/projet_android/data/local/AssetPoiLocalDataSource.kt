package com.example.projet_android.data.local

import android.content.Context
import com.example.projet_android.domain.model.Poi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AssetPoiLocalDataSource(
    private val context: Context,
    private val gson: Gson
) : PoiLocalDataSource {

    override fun loadPois(): List<Poi> {
        val json = context.assets.open("pois.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<Poi>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
