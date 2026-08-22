package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusBadge(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFF10B981)
    val inactiveColor = Color(0xFF94A3B8)
    val badgeBg = if (isActive) Color(0x1A10B981) else Color(0x1A94A3B8)
    val borderColor = if (isActive) Color(0x3310B981) else Color(0x3394A3B8)
    val dotColor = if (isActive) activeColor else inactiveColor

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(badgeBg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
