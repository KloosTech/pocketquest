package io.github.jackbeback.vm

import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import io.github.jackbeback.ui.*
import kotlinx.coroutines.flow.*
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.ui.state.*

class MapViewModel: ViewModel() {
    companion object{
        val instance = MapViewModel()
        var tiles = IntSize(9, 13)
    }

    private val _kaerMorhen = MutableStateFlow(MapState(1, tileSize = 30, fullWidth = 1960, fullHeight = 2560){
        scale(4f)
    }.apply {
        addLayer(makeStaticTileStreamProvider("files/tiles/KaerMorhen"))
    })

    val kaerMorhen: StateFlow<MapState> = _kaerMorhen.asStateFlow()

    val throneroom = MapState(1, tileSize = 100, fullWidth = 900, fullHeight = 1300){
        scale(4f)
        scroll(0.5, 0.3)
    }.apply {
        addLayer(makeStaticTileStreamProvider("files/tiles/Throneroom"))

        enableZooming()
    }

    private val _mapScale = MutableStateFlow(1f)
    val mapScale: StateFlow<Float> = _mapScale.asStateFlow()

    fun updateMapScale(scale: Float){
        _mapScale.update { scale }
    }
}
