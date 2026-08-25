package com.fesu.renjana.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TAG = "AppIcon"

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
fun AppIcon(
    packageName: String,
    apkPath: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showRenjanaBadge: Boolean = false,
    instanceColor: String? = null,
    instanceEmoji: String? = null
) {
    val context = LocalContext.current
    val bitmap = remember(packageName, apkPath) {
        val pm = context.packageManager
        // Try installed app first
        var drawable: Drawable? = try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "getApplicationIcon failed for $packageName: ${e.message}")
            null
        }
        // Fallback to APK file if provided
        if (drawable == null && apkPath != null) {
            try {
                val archiveInfo = pm.getPackageArchiveInfo(apkPath, 0)
                archiveInfo?.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = apkPath
                    appInfo.publicSourceDir = apkPath
                    drawable = pm.getApplicationIcon(appInfo)
                }
            } catch (e: Exception) {
                Log.w(TAG, "getApplicationIcon from APK failed for $apkPath: ${e.message}")
            }
        }
        try {
            drawable?.toImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "toImageBitmap failed: ${e.message}")
            null
        }
    }
    // Parse instanceColor safely — fallback to transparent (no ring)
    val ringColor: Color? = instanceColor?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
    }

    Box(modifier = modifier.size(size)) {
        val iconModifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (ringColor != null)
                    Modifier.border(2.dp, ringColor, RoundedCornerShape(12.dp))
                else
                    Modifier
            )
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "App icon",
                modifier = iconModifier
            )
        } else {
            Box(
                modifier = iconModifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        // ── Emoji overlay — TopStart (if present) ──────────────────────────
        if (!instanceEmoji.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ringColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size((size.value * 0.38f).coerceIn(16f, 22f).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = instanceEmoji,
                        fontSize = (size.value * 0.18f).coerceIn(8f, 11f).sp,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // ── Masked Renjana Container Badge — BottomEnd ─────────────────────
        if (showRenjanaBadge) {
            val badgeSize = (size.value * 0.40f).coerceIn(16f, 22f).dp
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0B1120).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ringColor ?: MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(badgeSize * 0.65f)) {
                        val w = this.size.width
                        val h = this.size.height
                        val badgeColor = ringColor ?: Color(0xFF38BDF8)

                        // Top face
                        val pTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.5f, 0f)
                            lineTo(w, h * 0.28f)
                            lineTo(w * 0.5f, h * 0.55f)
                            lineTo(0f, h * 0.28f)
                            close()
                        }
                        drawPath(pTop, color = badgeColor.copy(alpha = 0.95f))

                        // Left face
                        val pLeft = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, h * 0.28f)
                            lineTo(w * 0.5f, h * 0.55f)
                            lineTo(w * 0.5f, h)
                            lineTo(0f, h * 0.72f)
                            close()
                        }
                        drawPath(pLeft, color = badgeColor.copy(alpha = 0.7f))

                        // Right face
                        val pRight = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.5f, h * 0.55f)
                            lineTo(w, h * 0.28f)
                            lineTo(w, h * 0.72f)
                            lineTo(w * 0.5f, h)
                            close()
                        }
                        drawPath(pRight, color = badgeColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
