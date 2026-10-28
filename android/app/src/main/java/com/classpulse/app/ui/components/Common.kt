package com.classpulse.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classpulse.app.ui.theme.*

@Composable
fun PulsePage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(Paper)) {
        Canvas(Modifier.matchParentSize()) {
            val step = 30.dp.toPx()
            val dotRadius = 0.8.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    drawCircle(Ink.copy(alpha = 0.045f), dotRadius, Offset(x, y))
                    y += step
                }
                x += step
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Mint.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.93f, size.height * 0.06f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.93f, size.height * 0.06f),
            )
            drawCircle(
                color = Coral.copy(alpha = 0.045f),
                radius = size.width * 0.35f,
                center = Offset(-size.width * 0.08f, size.height * 0.8f),
            )
        }
        content()
    }
}

@Composable
fun PulseMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    dark: Boolean = false,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = if (dark) Cream else Teal,
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f),
        )
        val path = Path().apply {
            moveTo(w * 0.17f, h * 0.61f)
            cubicTo(
                w * 0.31f, h * 0.61f,
                w * 0.31f, h * 0.35f,
                w * 0.43f, h * 0.35f,
            )
            cubicTo(
                w * 0.55f, h * 0.35f,
                w * 0.55f, h * 0.68f,
                w * 0.67f, h * 0.68f,
            )
            cubicTo(
                w * 0.77f, h * 0.68f,
                w * 0.78f, h * 0.43f,
                w * 0.84f, h * 0.39f,
            )
        }
        drawPath(
            path = path,
            color = if (dark) Teal else Cream,
            style = Stroke(
                width = w * 0.072f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = Gold,
            radius = w * 0.072f,
            center = Offset(w * 0.82f, h * 0.22f),
        )
        drawCircle(
            color = Coral,
            radius = w * 0.045f,
            center = Offset(w * 0.22f, h * 0.79f),
        )
    }
}

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    compact: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PulseMark(size = if (compact) 32.dp else 40.dp, dark = dark)
        Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
        Column {
            Text(
                text = "课脉",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = if (dark) Cream else Ink,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "CLASS PULSE",
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Cream.copy(alpha = 0.65f) else Teal.copy(alpha = 0.75f),
                letterSpacing = 1.8.sp,
            )
        }
    }
}

@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Cream,
    contentColor: Color = Ink,
    borderColor: Color = Line,
    showBorder: Boolean = true,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (showBorder) BorderStroke(1.dp, borderColor) else null,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ClassPulseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Muted.copy(alpha = 0.7f)) } },
        modifier = modifier.fillMaxWidth(),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal,
            unfocusedBorderColor = Line,
            focusedLabelColor = Teal,
            unfocusedLabelColor = Muted,
            cursorColor = Teal,
            focusedContainerColor = Cream,
            unfocusedContainerColor = Cream,
            errorContainerColor = Cream,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    containerColor: Color = Teal,
    contentColor: Color = Cream,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = SurfaceColors.disabled,
            disabledContentColor = Muted,
        ),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Line),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private object SurfaceColors {
    val disabled = Color(0xFFE9E3D6)
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        }
        trailing?.invoke()
    }
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Mint,
    contentColor: Color = TealDeep,
    dotColor: Color? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@Composable
fun RoleBadge(isTeacher: Boolean, modifier: Modifier = Modifier) {
    StatusBadge(
        text = if (isTeacher) "教师" else "学生",
        modifier = modifier,
        containerColor = if (isTeacher) GoldSoft else Mint,
        contentColor = Ink,
        dotColor = if (isTeacher) Coral else Teal,
    )
}

@Composable
fun CodeTicket(
    label: String,
    code: String,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    containerColor: Color = Ink,
    codeColor: Color = Cream,
    accent: Color = Gold,
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.5f),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(
                        width = 1.3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = codeColor.copy(alpha = 0.65f),
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                code,
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 4.sp),
                color = codeColor,
            )
        }
        if (onCopy != null) {
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    onCopy()
                },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制",
                    tint = codeColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun AvatarCircle(name: String, size: Dp = 42.dp, containerColor: Color = Teal) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = com.classpulse.app.ui.util.Formats.initial(name),
            style = MaterialTheme.typography.titleMedium,
            color = Cream,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Mint.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.height(14.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun InlineError(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DangerSoft)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Danger))
        Spacer(Modifier.width(9.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Danger)
    }
}

@Composable
fun PulseTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Cream,
                border = BorderStroke(1.dp, Line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions?.let {
            Row(verticalAlignment = Alignment.CenterVertically, content = it)
        }
    }
}

@Composable
fun LoadingPane(message: String = "正在加载…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(30.dp),
            color = Teal,
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun RefreshIconButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Cream,
        border = BorderStroke(1.dp, Line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "刷新",
                tint = Teal,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier, color: Color = Line) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

@Composable
fun StatValue(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Ink) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Line,
    barColor: Color = Teal,
    height: Dp = 7.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(barColor),
        )
    }
}

@Composable
fun GradientDot(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CountBadge(count: Long, modifier: Modifier = Modifier, suffix: String = "人") {
    StatusBadge(
        text = "$count$suffix",
        modifier = modifier,
        containerColor = Paper,
        contentColor = InkSoft,
        dotColor = Teal,
    )
}
