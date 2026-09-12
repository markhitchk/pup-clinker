package com.harleytg.puppyclicker

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
internal fun HarleysStudiosBranding(
    modifier: Modifier = Modifier,
    contentDescription: String = "Harley's Studios"
) {
    Image(
        painter = painterResource(R.drawable.harleys_studios_icon),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
