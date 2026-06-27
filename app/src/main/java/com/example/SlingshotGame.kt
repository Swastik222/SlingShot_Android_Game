package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

enum class GameState {
    AIMING, FLYING, RESETTING, GAME_OVER
}

data class Target(var x: Float, var y: Float, val radius: Float = 45f, var active: Boolean = true)

@Composable
fun SlingshotGame(modifier: Modifier = Modifier) {
    var screenWidth by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    val slingshotCenter = remember(screenWidth, screenHeight) {
        Offset(screenWidth * 0.2f, screenHeight * 0.7f)
    }
    
    val maxDragDistance = 350f

    var ballPos by remember { mutableStateOf(Offset.Zero) }
    var ballVelocity by remember { mutableStateOf(Offset.Zero) }
    var gameState by remember { mutableStateOf(GameState.AIMING) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    var score by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var shotsRemaining by remember { mutableIntStateOf(10) }
    val targets = remember { mutableStateListOf<Target>() }

    // Initialize ball pos when slingshot center changes
    LaunchedEffect(slingshotCenter) {
        if (gameState == GameState.AIMING) {
            ballPos = slingshotCenter
            dragPos = slingshotCenter
        }
    }

    // Initialize targets when screen size is known
    LaunchedEffect(screenWidth, screenHeight) {
        if (screenWidth > 0 && screenHeight > 0 && targets.isEmpty()) {
            for (i in 0..4) {
                targets.add(
                    Target(
                        x = screenWidth * 0.5f + (Random.nextFloat() * screenWidth * 0.4f),
                        y = screenHeight * 0.2f + (Random.nextFloat() * screenHeight * 0.5f)
                    )
                )
            }
        }
    }

    // Game loop
    LaunchedEffect(gameState) {
        if (gameState == GameState.FLYING) {
            var lastTime = androidx.compose.runtime.withFrameNanos { it }
            while (gameState == GameState.FLYING) {
                androidx.compose.runtime.withFrameNanos { currentTime ->
                    val dt = (currentTime - lastTime) / 1_000_000_000f
                    lastTime = currentTime

                    // Physics (gravity)
                    val gravity = 1500f // px/s^2
                    ballVelocity = ballVelocity.copy(y = ballVelocity.y + gravity * dt)
                    ballPos = ballPos.plus(ballVelocity.times(dt))

                    // Target collisions
                    for (target in targets) {
                        if (target.active) {
                            val dist = hypot(ballPos.x - target.x, ballPos.y - target.y)
                            if (dist < 30f + target.radius) { // ball radius is 30f
                                target.active = false
                                score += 10
                            }
                        }
                    }

                    val allTargetsHit = targets.isNotEmpty() && targets.none { it.active }

                    // Floor collision (reset if out of bounds)
                    if (ballPos.y > screenHeight || ballPos.x > screenWidth || ballPos.x < 0) {
                        if (allTargetsHit) {
                            gameState = GameState.RESETTING
                        } else if (shotsRemaining <= 0) {
                            gameState = GameState.GAME_OVER
                        } else {
                            gameState = GameState.RESETTING
                        }
                    }

                    // Next level
                    if (allTargetsHit) {
                        level++
                        val newTargetCount = 4 + level // L1: 5, L2: 6, L3: 7
                        targets.clear()
                        for (i in 0 until newTargetCount) {
                            targets.add(
                                Target(
                                    x = screenWidth * 0.5f + (Random.nextFloat() * screenWidth * 0.4f),
                                    y = screenHeight * 0.2f + (Random.nextFloat() * screenHeight * 0.5f)
                                )
                            )
                        }
                        shotsRemaining += maxOf(2, 8 - level) // Gives fewer bonus shots at higher levels
                        gameState = GameState.RESETTING
                    }
                }
            }
        } else if (gameState == GameState.RESETTING) {
            delay(500)
            ballPos = slingshotCenter
            ballVelocity = Offset.Zero
            dragPos = slingshotCenter
            gameState = GameState.AIMING
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color(0xFF87CEEB)) // Sky blue background
        .onSizeChanged {
            screenWidth = it.width.toFloat()
            screenHeight = it.height.toFloat()
        }
    ) {
        if (screenWidth > 0 && screenHeight > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (gameState == GameState.AIMING) {
                                    val dist = hypot(offset.x - ballPos.x, offset.y - ballPos.y)
                                    if (dist < 100f) {
                                        dragPos = offset
                                        ballPos = offset
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (gameState == GameState.AIMING) {
                                    change.consume()
                                    var newDragPos = dragPos + dragAmount
                                    
                                    val dx = newDragPos.x - slingshotCenter.x
                                    val dy = newDragPos.y - slingshotCenter.y
                                    val dist = hypot(dx, dy)
                                    
                                    if (dist > maxDragDistance) {
                                        val scale = maxDragDistance / dist
                                        newDragPos = Offset(slingshotCenter.x + dx * scale, slingshotCenter.y + dy * scale)
                                    }
                                    
                                    dragPos = newDragPos
                                    ballPos = newDragPos
                                }
                            },
                            onDragEnd = {
                                if (gameState == GameState.AIMING) {
                                    val dx = slingshotCenter.x - dragPos.x
                                    val dy = slingshotCenter.y - dragPos.y
                                    
                                    if (hypot(dx, dy) > 20f && shotsRemaining > 0) {
                                        val launchMultiplier = 7.5f
                                        ballVelocity = Offset(dx * launchMultiplier, dy * launchMultiplier)
                                        shotsRemaining--
                                        gameState = GameState.FLYING
                                    } else {
                                        ballPos = slingshotCenter
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Draw ground
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(0f, screenHeight * 0.85f),
                    size = Size(screenWidth, screenHeight * 0.15f)
                )

                // Draw slingshot base
                drawLine(
                    color = Color(0xFF5D4037),
                    start = slingshotCenter.copy(y = screenHeight * 0.85f),
                    end = slingshotCenter,
                    strokeWidth = 30f,
                    cap = StrokeCap.Round
                )
                
                // Draw rubber band (back part)
                if (gameState == GameState.AIMING) {
                    drawLine(
                        color = Color(0xFFB71C1C),
                        start = slingshotCenter.copy(x = slingshotCenter.x - 20f),
                        end = ballPos,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw ball
                drawCircle(
                    color = Color(0xFF37474F), // Dark slate
                    radius = 30f,
                    center = ballPos
                )
                
                // Draw rubber band (front part)
                if (gameState == GameState.AIMING) {
                    drawLine(
                        color = Color(0xFFB71C1C),
                        start = slingshotCenter.copy(x = slingshotCenter.x + 20f),
                        end = ballPos,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw targets
                for (target in targets) {
                    if (target.active) {
                        // Outer red ring
                        drawCircle(
                            color = Color(0xFFE53935),
                            radius = target.radius,
                            center = Offset(target.x, target.y)
                        )
                        // Middle white ring
                        drawCircle(
                            color = Color.White,
                            radius = target.radius * 0.7f,
                            center = Offset(target.x, target.y)
                        )
                        // Inner red bullseye
                        drawCircle(
                            color = Color(0xFFE53935),
                            radius = target.radius * 0.35f,
                            center = Offset(target.x, target.y)
                        )
                    }
                }
            }
        }

        // Top UI
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Level: $level",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Shots: $shotsRemaining",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (shotsRemaining <= 2) Color.Red else Color.White
                )
            }
            Text(
                text = "Score: $score",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
        
        if (gameState == GameState.AIMING && score == 0 && targets.isNotEmpty()) {
             Text(
                text = "Drag the ball back to aim,\nrelease to shoot!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 120.dp)
                    .align(Alignment.TopCenter)
            )
        }
        if (gameState == GameState.GAME_OVER) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("GAME OVER", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.Red)
                Text("Final Score: $score", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Reached Level: $level", fontSize = 24.sp, color = Color.LightGray, modifier = Modifier.padding(bottom = 24.dp))
                
                androidx.compose.material3.Button(
                    onClick = {
                        score = 0
                        level = 1
                        shotsRemaining = 10
                        targets.clear()
                        for (i in 0..4) {
                            targets.add(
                                Target(
                                    x = screenWidth * 0.5f + (Random.nextFloat() * screenWidth * 0.4f),
                                    y = screenHeight * 0.2f + (Random.nextFloat() * screenHeight * 0.5f)
                                )
                            )
                        }
                        ballPos = slingshotCenter
                        dragPos = slingshotCenter
                        gameState = GameState.AIMING
                    }
                ) {
                    Text("Play Again", fontSize = 24.sp)
                }
            }
        }
    }
}
