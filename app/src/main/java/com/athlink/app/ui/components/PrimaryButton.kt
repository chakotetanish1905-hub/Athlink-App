package com.athlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.ui.theme.GradientEnd
import com.athlink.app.ui.theme.GradientStart

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val gradient = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = GradientStart.copy(0.4f))
            .clip(RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.background(gradient) else Modifier.background(Color.Gray.copy(0.3f))),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !isLoading,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GradientStart)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
