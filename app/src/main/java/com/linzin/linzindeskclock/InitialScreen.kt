package com.linzin.linzindeskclock

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.UriHandler

@Composable
fun InitialScreen(
    hasHingeSensor: Boolean = true,
    isClamshell: Boolean = false,
    onEnterClockDirectly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showNonFoldableDialog by remember { mutableStateOf(false) }

    val toastLiarFoldableText = stringResource(R.string.toast_liar_foldable)

    val onNotFoldableClick: () -> Unit = {
        if (hasHingeSensor && !isClamshell) {
            Toast.makeText(context, toastLiarFoldableText, Toast.LENGTH_SHORT).show()
        } else {
            showNonFoldableDialog = true
        }
    }

    val appIconBitmap = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }

    val youtubeUrl = "https://www.youtube.com/@Linzin"
    val facebookUrl = "https://www.facebook.com/linzin86/"
    val instagramUrl = "https://www.instagram.com/linzin.yt"
    val githubUrl = "https://github.com/linzin86/LinzinDeskClock/"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.size(88.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.initial_prompt_fold),
                            color = Color.White,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            onClick = onNotFoldableClick,
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF24242B),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_not_foldable),
                                color = Color(0xFF81C7D4),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Made by: Linzin 阿哲, MaRi",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialMediaButton(
                                iconRes = R.drawable.ic_youtube,
                                onClick = { uriHandler.openUriSafely(youtubeUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_facebook,
                                onClick = { uriHandler.openUriSafely(facebookUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_instagram,
                                onClick = { uriHandler.openUriSafely(instagramUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_github,
                                onClick = { uriHandler.openUriSafely(githubUrl) }
                            )
                        }
                    }
                }
            } else {
                // 自適應單一垂直容器：防止方塊比例內螢幕文字與按鈕重疊
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.size(80.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.initial_prompt_fold),
                            color = Color.White,
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Surface(
                            onClick = onNotFoldableClick,
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF24242B)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_not_foldable),
                                color = Color(0xFF81C7D4),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Text(
                            text = "Made by: Linzin 阿哲, MaRi",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SocialMediaButton(
                                iconRes = R.drawable.ic_youtube,
                                onClick = { uriHandler.openUriSafely(youtubeUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_facebook,
                                onClick = { uriHandler.openUriSafely(facebookUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_instagram,
                                onClick = { uriHandler.openUriSafely(instagramUrl) }
                            )
                            SocialMediaButton(
                                iconRes = R.drawable.ic_github,
                                onClick = { uriHandler.openUriSafely(githubUrl) }
                            )
                        }
                    }
                }
            }

            if (showNonFoldableDialog) {
                NonFoldableDialog(
                    onDismiss = { showNonFoldableDialog = false },
                    onAccept = {
                        showNonFoldableDialog = false
                        onEnterClockDirectly()
                    }
                )
            }
        }
    }
}

/** 裝置沒有可開啟連結的 App 時不閃退 */
fun UriHandler.openUriSafely(url: String) {
    try {
        openUri(url)
    } catch (e: Exception) {
        Log.w("DeskClock", "No app can open $url", e)
    }
}

@Composable
fun SocialMediaButton(
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF1E1E24),
        modifier = modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
