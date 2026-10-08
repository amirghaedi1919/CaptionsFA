package com.daboua.captions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ */
/* فونت‌ها                                                              */
/* ------------------------------------------------------------------ */

private data class FontOption(
    val key: String,
    val label: String,
    val family: FontFamily
)

private val fontOptions = listOf(
    FontOption("SANS", "ساده", FontFamily.SansSerif),
    FontOption("SERIF", "کتابی", FontFamily.Serif),
    FontOption("MONO", "تایپی", FontFamily.Monospace),
    FontOption("CURSIVE", "دست‌نویس", FontFamily.Cursive)
)

fun captionFontFamily(key: String): FontFamily =
    fontOptions.firstOrNull { it.key == key }?.family
        ?: FontFamily.SansSerif

/* ------------------------------------------------------------------ */
/* انیمیشن‌ها (نام فارسی)                                               */
/* ------------------------------------------------------------------ */

private fun animationLabel(a: CaptionAnimation): String =
    when (a) {
        CaptionAnimation.NONE -> "بدون انیمیشن"
        CaptionAnimation.FADE -> "محو شدن"
        CaptionAnimation.SLIDE_UP -> "از پایین"
        CaptionAnimation.SLIDE_DOWN -> "از بالا"
        CaptionAnimation.SLIDE_LEFT -> "از چپ"
        CaptionAnimation.SLIDE_RIGHT -> "از راست"
    }

/* ------------------------------------------------------------------ */
/* قالب‌های آماده                                                       */
/* ------------------------------------------------------------------ */

private data class StylePreset(
    val label: String,
    val style: CaptionStyle
)

private val presets = listOf(
    StylePreset(
        "کلاسیک",
        CaptionStyle(
            fontSize = 26f, textColor = 0xFFFFFFFF,
            backgroundColor = 0x99000000, bold = true,
            animation = CaptionAnimation.FADE
        )
    ),
    StylePreset(
        "زرد سینمایی",
        CaptionStyle(
            fontSize = 28f, textColor = 0xFFFFD54F,
            backgroundColor = 0x00000000, bold = true,
            animation = CaptionAnimation.SLIDE_UP
        )
    ),
    StylePreset(
        "نئون",
        CaptionStyle(
            fontSize = 28f, textColor = 0xFF00E5FF,
            backgroundColor = 0xB3121226, bold = true,
            animation = CaptionAnimation.FADE
        )
    ),
    StylePreset(
        "صورتی",
        CaptionStyle(
            fontSize = 27f, textColor = 0xFFFFFFFF,
            backgroundColor = 0xE6E91E63, bold = true,
            animation = CaptionAnimation.SLIDE_UP
        )
    ),
    StylePreset(
        "مینیمال",
        CaptionStyle(
            fontSize = 24f, textColor = 0xFFFFFFFF,
            backgroundColor = 0x00000000, bold = false,
            animation = CaptionAnimation.NONE
        )
    )
)

private val textColors = listOf(
    0xFFFFFFFF, 0xFFFFD54F, 0xFF00E5FF, 0xFF69F0AE,
    0xFFFF80AB, 0xFFFF5252, 0xFF000000
)

private val backgroundColors = listOf(
    0x00000000L, 0x99000000L, 0xE6000000L, 0xE6E91E63L,
    0xE61565C0L, 0xE62E7D32L, 0xE6F9A825L
)

/* ------------------------------------------------------------------ */
/* اجزای کوچک                                                           */
/* ------------------------------------------------------------------ */

private val Accent = Color(0xFF7C4DFF)
private val CardBg = Color(0xFF1C1B2B)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFB8B5D6),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else Color(0xFF2A2940))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ColorDot(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit
) {
    val transparent = (color shr 24) == 0L
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (transparent) Color(0xFF2A2940) else Color(color))
            .border(
                BorderStroke(
                    if (selected) 3.dp else 1.dp,
                    if (selected) Accent else Color(0xFF5A5878)
                ),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (transparent) {
            Text("∅", color = Color.LightGray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/* ------------------------------------------------------------------ */
/* پنل اصلی استایل                                                      */
/* ------------------------------------------------------------------ */

/**
 * پنل استایل کل پروژه.
 * هر تغییری که اینجا داده شود روی همه‌ی کپشن‌ها اعمال می‌شود.
 */
@Composable
fun GlobalStylePanel(
    style: CaptionStyle,
    quality: LocalWhisper.Quality,
    onStyleChange: (CaptionStyle) -> Unit,
    onQualityChange: (LocalWhisper.Quality) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Text(
                "🎨 استایل زیرنویس",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            SectionTitle("قالب آماده")
            ChipRow {
                presets.forEach { preset ->
                    Chip(preset.label, false) {
                        onStyleChange(
                            preset.style.copy(
                                position = style.position,
                                alignment = style.alignment,
                                fontFamily = style.fontFamily
                            )
                        )
                    }
                }
            }

            SectionTitle("فونت")
            ChipRow {
                fontOptions.forEach { f ->
                    Chip(f.label, style.fontFamily == f.key) {
                        onStyleChange(style.copy(fontFamily = f.key))
                    }
                }
            }

            SectionTitle("رنگ متن")
            ChipRow {
                textColors.forEach { c ->
                    ColorDot(c, style.textColor == c) {
                        onStyleChange(style.copy(textColor = c))
                    }
                }
            }

            SectionTitle("رنگ پس‌زمینه")
            ChipRow {
                backgroundColors.forEach { c ->
                    ColorDot(c, style.backgroundColor == c) {
                        onStyleChange(style.copy(backgroundColor = c))
                    }
                }
            }

            SectionTitle("اندازه: ${style.fontSize.roundToInt()}")
            Slider(
                value = style.fontSize,
                onValueChange = { onStyleChange(style.copy(fontSize = it)) },
                valueRange = 14f..60f
            )

            Chip(
                if (style.bold) "✓ ضخیم" else "ضخیم",
                style.bold
            ) { onStyleChange(style.copy(bold = !style.bold)) }

            SectionTitle("نحوه‌ی آمدن و رفتن زیرنویس")
            ChipRow {
                CaptionAnimation.entries.forEach { a ->
                    Chip(animationLabel(a), style.animation == a) {
                        onStyleChange(style.copy(animation = a))
                    }
                }
            }

            SectionTitle(
                "سرعت انیمیشن: ${style.animationDuration} میلی‌ثانیه"
            )
            Slider(
                value = style.animationDuration.toFloat(),
                onValueChange = {
                    onStyleChange(style.copy(animationDuration = it.roundToInt()))
                },
                valueRange = 100f..1000f
            )

            SectionTitle("موقعیت عمودی: ${(style.position * 100).roundToInt()}٪")
            Slider(
                value = style.position,
                onValueChange = { onStyleChange(style.copy(position = it)) },
                valueRange = 0.05f..0.95f
            )

            SectionTitle("دقت تشخیص گفتار")
            ChipRow {
                Chip(
                    "سریع",
                    quality == LocalWhisper.Quality.FAST
                ) { onQualityChange(LocalWhisper.Quality.FAST) }
                Chip(
                    "دقیق (پیشنهادی)",
                    quality == LocalWhisper.Quality.ACCURATE
                ) { onQualityChange(LocalWhisper.Quality.ACCURATE) }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "حالت «دقیق» بار اول مدل بزرگ‌تری دانلود می‌کند ولی برای فارسی خیلی کم‌اشتباه‌تر است.",
                color = Color(0xFF8E8BB0),
                fontSize = 11.sp
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* کارت سرور و تعداد کلمه‌ی هر زیرنویس                                  */
/* ------------------------------------------------------------------ */

@Composable
fun ServerCard() {

    val context = androidx.compose.ui.platform.LocalContext.current

    var url by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(ServerSettings.url(context))
    }
    var key by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(ServerSettings.apiKey(context))
    }
    var words by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            ServerSettings.wordsPerCaption(context)
        )
    }

    fun persist() = ServerSettings.save(context, url, key, words)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Text(
                "⚡ تشخیص سریع روی سرور",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "اگر آدرس سرور را وارد کنی، زیرنویس در چند ثانیه آماده می‌شود. " +
                    "خالی بگذاری، تشخیص روی گوشی انجام می‌شود.",
                color = Color(0xFF8E8BB0),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            androidx.compose.material3.OutlinedTextField(
                value = url,
                onValueChange = { url = it; persist() },
                label = { Text("آدرس سرور (مثلاً 1.2.3.4:8000)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            androidx.compose.material3.OutlinedTextField(
                value = key,
                onValueChange = { key = it; persist() },
                label = { Text("رمز دسترسی (API Key)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("تعداد کلمه در هر زیرنویس")
            ChipRow {
                listOf(1, 2, 3, 4, 5, 6, 8).forEach { n ->
                    Chip("$n", words == n) {
                        words = n
                        persist()
                    }
                }
            }
        }
    }
}
