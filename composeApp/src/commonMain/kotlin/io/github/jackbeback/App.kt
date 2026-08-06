package io.github.jackbeback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.jackbeback.data.*
import io.github.jackbeback.ui.*
import io.github.jackbeback.vm.*
import kotlinx.io.*
import org.jetbrains.compose.resources.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.*
import ovh.plrapps.mapcompose.ui.state.*

import pocketquest.composeapp.generated.resources.Res
import pocketquest.composeapp.generated.resources.compose_multiplatform
import kotlin.random.*

@Composable
@Preview
fun App() {
    val unitVm = remember { UnitViewModel.instance }
    LaunchedEffect(Unit) {
        unitVm.load()
    }
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GameScreen()
        }
    }
}



