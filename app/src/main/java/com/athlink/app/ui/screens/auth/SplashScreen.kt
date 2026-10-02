package com.athlink.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(2200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AthlinkDeepBlue, AthlinkBlack))),
        contentAlignment = Alignment.Center
    ) {
        // Background decorative circles
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = 80.dp, y = (-120).dp)
                .background(AthlinkOrange.copy(alpha = 0.06f), androidx.compose.foundation.shape.CircleShape)
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-100).dp, y = 160.dp)
                .background(GradientEnd.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            // Logo circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                        androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsSoccer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "ATHLINK",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 6.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your Sports Community",
                fontSize = 15.sp,
                color = AthlinkMedGray,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(48.dp))

            CircularProgressIndicator(
                color = AthlinkOrange,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
