package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.NexGenAmber
import com.example.ui.theme.NexGenCobalt
import com.example.ui.theme.NexGenCoral
import com.example.ui.theme.NexGenCyanGlow
import com.example.util.PrintAndExportUtils
import kotlin.math.abs

@Composable
fun GlowingNexGenLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logoGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .size(size + 8.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NexGenCyanGlow.copy(alpha = glowAlpha * 0.65f),
                        NexGenCobalt.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.drawable.img_nexgen_logo,
            contentDescription = "NexGen Market Glowing Logo",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            NexGenCyanGlow.copy(alpha = glowAlpha),
                            NexGenCoral.copy(alpha = glowAlpha * 0.8f)
                        )
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                ),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ProductImageBox(
    imageKey: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    when {
        imageKey.startsWith("http") || imageKey.startsWith("content:") -> {
            AsyncImage(
                model = imageKey,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_prod_smartphone),
                error = painterResource(id = R.drawable.img_prod_smartphone)
            )
        }
        else -> {
            val resId = when (imageKey.lowercase()) {
                "audio", "headphones", "earbuds" -> R.drawable.img_prod_audio
                "watch", "smartwatch", "wearables" -> R.drawable.img_prod_watch
                "banner_flash" -> R.drawable.img_banner_flash_sale
                "banner_mega" -> R.drawable.img_banner_mega_deals
                "nexgen_logo" -> R.drawable.img_nexgen_logo
                else -> R.drawable.img_prod_smartphone
            }
            AsyncImage(
                model = resId,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun DiscountBadge(percent: Long, modifier: Modifier = Modifier) {
    if (percent <= 0L) return
    Surface(
        modifier = modifier,
        color = NexGenCoral,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "-$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StarRatingRow(
    rating: Double,
    reviewCount: Long? = null,
    soldCount: Long? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val fullStars = rating.toInt().coerceIn(0, 5)
        repeat(5) { idx ->
            Icon(
                imageVector = if (idx < fullStars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = NexGenAmber,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = String.format(java.util.Locale.US, "%.1f", rating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reviewCount != null) {
            Text(
                text = " ($reviewCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (soldCount != null && soldCount > 0) {
            Text(
                text = " • $soldCount sold",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PriceDisplay(
    sellingPrice: Double,
    originalPrice: Double,
    currencyCode: String = "KES",
    large: Boolean = false
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = PrintAndExportUtils.formatMoney(sellingPrice, currencyCode),
            style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = NexGenCoral,
            fontWeight = FontWeight.Bold
        )
        if (originalPrice > sellingPrice) {
            Text(
                text = PrintAndExportUtils.formatMoney(originalPrice, currencyCode),
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (large) 4.dp else 1.dp)
            )
        }
    }
}

@Composable
fun BarcodeStrip(
    code: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
        ) {
            val seed = code.ifBlank { "600123456789" }
            val bars = 46
            val barUnit = size.width / (bars * 2f)
            var x = 0f
            for (i in 0 until bars) {
                val ch = seed[i % seed.length].code
                val wFactor = ((abs(ch * (i + 3)) % 3) + 1).toFloat()
                val barW = (barUnit * wFactor).coerceAtMost(size.width - x)
                if (i % 2 == 0 && barW > 0f) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x, 0f),
                        size = Size(barW, size.height)
                    )
                }
                x += barUnit * (wFactor * 0.9f + 0.8f)
                if (x >= size.width) break
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = code.ifBlank { "NO BARCODE" },
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black
        )
    }
}
