package com.mogg.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mogg.app.R

/** Табличка «YES!» — декор. */
@Composable
fun YesSign(width: Dp = 90.dp, rotation: Float = -8f, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.yes_sign),
        contentDescription = null,
        modifier = modifier.rotate(rotation).width(width),
    )
}
