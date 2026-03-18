package de.jackbeback.pocketquest.ui.overworld

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ovh.plrapps.mapcompose.ui.MapUI

@Composable
fun OverworldScreen(viewModel: OverworldViewModel) {
    MapUI(
        modifier = Modifier.fillMaxSize(),
        state = viewModel.mapState,
    )
}
