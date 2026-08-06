package io.github.jackbeback.vm

import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import io.github.jackbeback.data.Position
import io.github.jackbeback.util.getDistanceTo
import io.github.jackbeback.util.toTileIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.io.Buffer
import org.jetbrains.compose.resources.ExperimentalResourceApi
import pocketquest.composeapp.generated.resources.Res

class OverlayViewModel : ViewModel() {
    private var center: IntSize? = null
    private var range: Int? = null

    private val _overlayVersion = MutableStateFlow(0)
    val overlayVersion: StateFlow<Int> = _overlayVersion.asStateFlow()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun getOverlayFor(row: Int, col: Int, zoomLvl: Int): Buffer? {
        if (center == null || range == null) return null
        val delta = center!!.getDistanceTo(IntSize(row, col))
        if (delta < range!!) {
            return null
        } else {
            val buffer = Buffer()
            Res.readBytes("drawable/red_30.png").let {
                buffer.write(it)
                buffer
            }
            return buffer
        }
    }

    fun showRangeOverlay(center: Position, range: Int) {
        this.center = center.toTileIndex()
        this.range = range
        _overlayVersion.update { it + 1 }
    }

    fun reset() {
        this.center = null
        this.range = null
        _overlayVersion.update { it + 1 }
    }

    companion object {
        val instance = OverlayViewModel()
    }
}
