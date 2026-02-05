package com.mygdx.game.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MagnetExperimentScreen(
    azimuth: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Text(
            text = azimuth.toString(),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
