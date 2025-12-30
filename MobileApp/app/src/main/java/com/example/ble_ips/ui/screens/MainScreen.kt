package com.example.ble_ips.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colors matching the design
private val BackgroundDark = Color(0xFF111318)
private val SurfaceDark = Color(0xFF1C1F27)
private val PrimaryBlue = Color(0xFF135BEC)
private val TextWhite = Color.White
private val TextGray = Color(0xFF58627A)
private val GridColor = Color(0xFF1C1F27)
private val WallColor = Color(0xFF3B4354)
private val GreenActive = Color(0xFF4ADE80)
private val YellowActive = Color(0xFFFACC15)

@Composable
fun MainScreen(
    mqttStatus: String,
    beaconStatus: String,
    lastPublishStatus: String,
    lastReceivedStatus: String,
    smoothedPosition: State<Offset?>
) {
    // Extract beacon count from beaconStatus (e.g., "Beacons: 2/3 visible")
    val beaconCount = beaconStatus.filter { it.isDigit() }.take(1).toIntOrNull() ?: 0
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Grid Pattern Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSize = 20.dp.toPx()
            val dotRadius = 1.dp.toPx()
            
            for (x in 0..(size.width / gridSize).toInt()) {
                for (y in 0..(size.height / gridSize).toInt()) {
                    drawCircle(
                        color = GridColor,
                        radius = dotRadius,
                        center = Offset(x * gridSize, y * gridSize)
                    )
                }
            }
        }

        // Map Area with Floor Plan and Beacons
        MapDisplay(position = smoothedPosition, beaconCount = beaconCount)

        // Top Status HUD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            StatusHUD(beaconCount = beaconCount, mqttStatus = mqttStatus)
        }

        // FAB - My Location
        FloatingActionButton(
            onClick = { /* Center on user */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 200.dp),
            containerColor = PrimaryBlue,
            contentColor = TextWhite,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "My Location",
                modifier = Modifier.size(24.dp)
            )
        }

        // Bottom Sheet
        BottomInfoSheet(
            position = smoothedPosition.value,
            lastReceivedStatus = lastReceivedStatus,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun StatusHUD(beaconCount: Int, mqttStatus: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SurfaceDark.copy(alpha = 0.8f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bluetooth Status
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = if (beaconCount > 0) GreenActive else Color.Gray,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "$beaconCount Beacons Active",
            color = TextWhite.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.2f))
        )

        // Signal Strength
        Icon(
            imageVector = Icons.Default.SignalCellularAlt,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (mqttStatus.contains("Connected")) "Connected" else "Offline",
            color = TextWhite.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MapDisplay(position: State<Offset?>, beaconCount: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Floor Plan Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 40.dp.toPx()
            val mapWidth = size.width - 2 * padding
            val mapHeight = size.height - 2 * padding - 200.dp.toPx() // Leave room for bottom sheet

            // Draw floor plan walls
            val wallStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(4.dp.toPx()))
            
            // Outer walls
            drawRect(
                color = WallColor.copy(alpha = 0.4f),
                topLeft = Offset(padding, padding),
                size = Size(mapWidth, mapHeight),
                style = wallStroke
            )

            // Inner room divisions
            // Left room
            drawPath(
                path = Path().apply {
                    moveTo(padding, padding + mapHeight * 0.35f)
                    lineTo(padding + mapWidth * 0.35f, padding + mapHeight * 0.35f)
                    lineTo(padding + mapWidth * 0.35f, padding + mapHeight * 0.6f)
                    lineTo(padding, padding + mapHeight * 0.6f)
                },
                color = WallColor.copy(alpha = 0.4f),
                style = wallStroke
            )

            // Top right corner room
            drawPath(
                path = Path().apply {
                    moveTo(padding + mapWidth * 0.55f, padding)
                    lineTo(padding + mapWidth * 0.55f, padding + mapHeight * 0.25f)
                    lineTo(padding + mapWidth, padding + mapHeight * 0.25f)
                },
                color = WallColor.copy(alpha = 0.4f),
                style = wallStroke
            )

            // Bottom right corner room
            drawPath(
                path = Path().apply {
                    moveTo(padding + mapWidth * 0.55f, padding + mapHeight)
                    lineTo(padding + mapWidth * 0.55f, padding + mapHeight * 0.7f)
                    lineTo(padding + mapWidth, padding + mapHeight * 0.7f)
                },
                color = WallColor.copy(alpha = 0.4f),
                style = wallStroke
            )
        }

        // Room Labels using Compose Text
        Text(
            text = "Conf Room A",
            color = TextGray,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 60.dp)
        )
        
        Text(
            text = "Lobby",
            color = TextGray,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 60.dp)
        )

        // Beacon Indicators - positioned at edges
        BeaconMarker(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 60.dp, y = 120.dp),
            isActive = beaconCount >= 1,
            color = GreenActive
        )
        
        BeaconMarker(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-60).dp, y = 120.dp),
            isActive = beaconCount >= 2,
            color = GreenActive
        )
        
        BeaconMarker(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-280).dp),
            isActive = beaconCount >= 3,
            color = if (beaconCount >= 3) GreenActive else YellowActive
        )

        // User Position Marker
        position.value?.let { pos ->
            UserMarkerWithLabel(
                normalizedX = pos.x,
                normalizedY = 1f - pos.y, // Invert Y axis
                paddingDp = 40.dp,
                bottomSheetHeightDp = 200.dp
            )
        }
    }
}

@Composable
private fun BeaconMarker(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    color: Color
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(SurfaceDark)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isActive) color else Color.Gray)
        )
    }
}

@Composable
private fun UserMarkerWithLabel(
    normalizedX: Float,
    normalizedY: Float,
    paddingDp: Dp,
    bottomSheetHeightDp: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = paddingDp, end = paddingDp, top = paddingDp, bottom = bottomSheetHeightDp + paddingDp)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        
        val userXPx = normalizedX * widthPx
        val userYPx = normalizedY * heightPx
        
        val userXDp = with(density) { userXPx.toDp() }
        val userYDp = with(density) { userYPx.toDp() }

        // User Marker Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(userXPx, userYPx)

            // Pulse ring
            drawCircle(
                color = PrimaryBlue.copy(alpha = pulseAlpha),
                radius = 40.dp.toPx() * pulseScale,
                center = center
            )

            // Direction cone
            val conePath = Path().apply {
                moveTo(userXPx, userYPx - 50.dp.toPx())
                lineTo(userXPx - 15.dp.toPx(), userYPx)
                lineTo(userXPx + 15.dp.toPx(), userYPx)
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.verticalGradient(
                    colors = listOf(PrimaryBlue.copy(alpha = 0.4f), PrimaryBlue.copy(alpha = 0f)),
                    startY = userYPx - 50.dp.toPx(),
                    endY = userYPx
                )
            )

            // Core dot
            drawCircle(
                color = PrimaryBlue,
                radius = 10.dp.toPx(),
                center = center
            )
            drawCircle(
                color = TextWhite,
                radius = 10.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // User Label (positioned below the user marker)
        Box(
            modifier = Modifier
                .offset(x = userXDp - 20.dp, y = userYDp + 20.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "User",
                color = TextWhite.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BottomInfoSheet(
    position: Offset?,
    lastReceivedStatus: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(SurfaceDark.copy(alpha = 0.95f))
            .padding(bottom = 32.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }

        // Content
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Floor 1, West Wing",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lastReceivedStatus.ifEmpty { "Waiting for data..." },
                        color = TextWhite.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = { /* Settings */ }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextWhite.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Coordinates Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoordinateCard(
                    label = "POS X",
                    value = position?.let { String.format("%.1fm", it.x * 10) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                CoordinateCard(
                    label = "POS Y",
                    value = position?.let { String.format("%.1fm", it.y * 10) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                CoordinateCard(
                    label = "ACC",
                    value = if (position != null) "High" else "N/A",
                    valueColor = if (position != null) GreenActive else TextGray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CoordinateCard(
    label: String,
    value: String,
    valueColor: Color = TextWhite,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = TextWhite.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
