package io.github.jackbeback.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ActionCircle(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(
                color = Color(0xFF4CAF50),  // Grün als Alternative
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = Color(0xFF388E3C),  // Dunkleres Grün
                shape = CircleShape
            )
            .shadow(
                elevation = 4.dp,
                shape = CircleShape
            )
    )
}

