package com.maksym.financetracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksym.financetracker.domain.*
import kotlinx.datetime.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Editorial Minimalism Design Tokens
 */
object EditorialColors {
    // Core Warm Palette
    val WarmIvory = Color(0xFFF5F3ED)
    val SoftStone = Color(0xFFE8E6DD)
    val DeepCharcoal = Color(0xFF292B29)
    val ForestGreen = Color(0xFF253B32)
    val MutedSage = Color(0xFF8D9C88)
    val RestrainedTerracotta = Color(0xFFB97560)

    // Neutral Tints & Surfaces (Light)
    val LightCard = Color(0xFFFCFBF8)
    val LightElevated = Color(0xFFF0EEE6)
    val LightBorder = Color(0xFFDFDDD4)
    val LightSubtleText = Color(0xFF737871)
    val LightInactivePill = Color(0xFFECEAE2)

    // Dark Mode Counterparts
    val DarkBackground = Color(0xFF181A19)
    val DarkCard = Color(0xFF202321)
    val DarkElevated = Color(0xFF292D2A)
    val DarkBorder = Color(0xFF333834)
    val DarkText = Color(0xFFF4F3ED)
    val DarkSubtleText = Color(0xFF9EA39C)
    val DarkInactivePill = Color(0xFF2B2F2C)
    val DarkForestGreen = Color(0xFF7CA692)
    val DarkTerracotta = Color(0xFFD48873)
    val DarkMutedSage = Color(0xFFA5B7A0)
}

val LightEditorialColorScheme = lightColorScheme(
    primary = EditorialColors.ForestGreen,
    onPrimary = Color.White,
    primaryContainer = EditorialColors.SoftStone,
    onPrimaryContainer = EditorialColors.DeepCharcoal,
    secondary = EditorialColors.MutedSage,
    onSecondary = Color.White,
    secondaryContainer = EditorialColors.LightElevated,
    onSecondaryContainer = EditorialColors.DeepCharcoal,
    tertiary = EditorialColors.RestrainedTerracotta,
    onTertiary = Color.White,
    background = EditorialColors.WarmIvory,
    onBackground = EditorialColors.DeepCharcoal,
    surface = EditorialColors.LightCard,
    onSurface = EditorialColors.DeepCharcoal,
    surfaceVariant = EditorialColors.LightElevated,
    onSurfaceVariant = EditorialColors.LightSubtleText,
    outline = EditorialColors.LightBorder,
    error = EditorialColors.RestrainedTerracotta,
    onError = Color.White
)

val DarkEditorialColorScheme = darkColorScheme(
    primary = EditorialColors.DarkForestGreen,
    onPrimary = EditorialColors.DarkBackground,
    primaryContainer = EditorialColors.DarkElevated,
    onPrimaryContainer = EditorialColors.DarkText,
    secondary = EditorialColors.DarkMutedSage,
    onSecondary = EditorialColors.DarkBackground,
    secondaryContainer = EditorialColors.DarkCard,
    onSecondaryContainer = EditorialColors.DarkText,
    tertiary = EditorialColors.DarkTerracotta,
    onTertiary = Color.White,
    background = EditorialColors.DarkBackground,
    onBackground = EditorialColors.DarkText,
    surface = EditorialColors.DarkCard,
    onSurface = EditorialColors.DarkText,
    surfaceVariant = EditorialColors.DarkElevated,
    onSurfaceVariant = EditorialColors.DarkSubtleText,
    outline = EditorialColors.DarkBorder,
    error = EditorialColors.DarkTerracotta,
    onError = Color.White
)

val EditorialTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    titleSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.0.sp
    )
)

val EditorialShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LedgerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) DarkEditorialColorScheme else LightEditorialColorScheme,
        typography = EditorialTypography,
        shapes = EditorialShapes,
        content = content
    )
}

/**
 * Editorial Card Container with restrained border & soft surface
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Boolean = true,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

/**
 * Editorial Section Title with Serif typography and subtle metadata subtitle
 */
@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * Editorial Header for top of screens
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * Minimalist Thin Divider
 */
@Composable
fun EditorialDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    )
}

/**
 * Editorial Chip / Pill Filter
 */
@Composable
fun EditorialChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isLight) EditorialColors.LightInactivePill else EditorialColors.DarkInactivePill
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = bgColor,
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

/**
 * Empty State with editorial typography and quiet aesthetics
 */
@Composable
fun EmptyState(
    title: String,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Panel(
        padding = 32.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "◈",
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Notification banner (retained with editorial refinement)
 */
@Composable
fun Notice(text: String, error: Boolean, dismiss: () -> Unit) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (error) "✕ $text" else "✓ $text",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = dismiss) {
                Text("Dismiss", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Formatted Money Label with editorial tabular digits
 */
@Composable
fun MoneyLabel(
    money: Money,
    large: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    showPrefix: Boolean = false,
    isIncome: Boolean = false
) {
    val prefix = if (showPrefix) {
        if (isIncome) "+" else "−"
    } else ""

    Text(
        text = prefix + money.display(),
        style = if (large) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleMedium,
        fontWeight = if (large) FontWeight.SemiBold else FontWeight.SemiBold,
        color = color
    )
}

/**
 * Editorial Dropdown Picker
 */
@Composable
fun <T> Picker(
    label: String,
    value: T,
    options: List<T>,
    text: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text(value),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text(option),
                            fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Editorial Text Input
 */
@Composable
fun Input(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Editorial Month Navigation
 */
@Composable
fun MonthPicker(month: LocalDate, onChange: (LocalDate) -> Unit) {
    val monthName = month.month.name.lowercase().replaceFirstChar { it.uppercase() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            onClick = { onChange(month.minus(1, DateTimeUnit.MONTH)) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("←", style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            text = "$monthName ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            onClick = { onChange(month.plus(1, DateTimeUnit.MONTH)) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("→", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Editorial Transaction Row
 */
@Composable
fun TransactionRow(tx: Transaction, ledger: Ledger, onClick: () -> Unit) {
    val category = ledger.categories.find { it.id == tx.categoryId }
    val account = ledger.accounts.find { it.id == tx.accountId }
    val destAccount = ledger.accounts.find { it.id == tx.destinationId }
    val isIncome = tx.type == TransactionType.INCOME
    val isTransfer = tx.type == TransactionType.TRANSFER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon / Category Glyph
        val badgeBg = if (isTransfer) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        } else {
            category?.let { Color(it.color).copy(alpha = 0.15f) } ?: MaterialTheme.colorScheme.secondaryContainer
        }
        val badgeColor = if (isTransfer) {
            MaterialTheme.colorScheme.secondary
        } else {
            category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSecondaryContainer
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeBg,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isTransfer) "⇄" else category?.icon ?: "●",
                    fontSize = 18.sp,
                    color = badgeColor
                )
            }
        }

        // Details
        Column(modifier = Modifier.weight(1f)) {
            val title = when {
                tx.description.isNotBlank() -> tx.description
                isTransfer -> "Transfer to ${destAccount?.name ?: "Account"}"
                category != null -> category.name
                else -> "Transaction"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = when {
                isTransfer -> "${account?.name ?: "Account"} → ${destAccount?.name ?: "Account"}"
                category != null -> "${account?.name ?: "Account"} · ${category.name}"
                else -> account?.name ?: "Account"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Amount & Date
        Column(horizontalAlignment = Alignment.End) {
            val amountColor = when {
                isIncome -> MaterialTheme.colorScheme.primary
                isTransfer -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface
            }
            val sign = when (tx.type) {
                TransactionType.INCOME -> "+"
                TransactionType.EXPENSE -> "−"
                TransactionType.TRANSFER -> ""
            }

            Text(
                text = "$sign${tx.amount.display()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
            Text(
                text = tx.date.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Editorial Budget Bar with Left-of-Budget prominence
 */
@Composable
fun BudgetBar(progress: BudgetProgress, name: String) {
    val ratio = (progress.spent.minor.toDouble() / progress.budget.limit.minor).coerceIn(0.0, 1.0).toFloat()
    val isOver = progress.remaining.minor < 0
    val barColor = when {
        isOver -> MaterialTheme.colorScheme.error
        progress.warning -> EditorialColors.RestrainedTerracotta
        else -> MaterialTheme.colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (isOver) {
                        "${progress.remaining.copy(minor = -progress.remaining.minor).display()} over budget"
                    } else {
                        "${progress.remaining.display()} left of ${progress.budget.limit.display()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = barColor
            )
        }

        // Clean Two-tone Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun SafeBudgetBar(ledger: Ledger, budget: Budget) {
    val progress = runCatching { ledger.progress(budget).also { it.remaining } }.getOrNull()
    if (progress == null) {
        Text("Budget totals exceed the supported range", color = MaterialTheme.colorScheme.error)
    } else {
        BudgetBar(progress, ledger.categories.first { it.id == budget.categoryId }.name)
    }
}

/**
 * Custom Canvas Cash Flow Bezier Curve Chart
 */
@Composable
fun CashFlowLineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val width = size.width
        val height = size.height

        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = if (points.size > 1) width / (points.size - 1) else width

        val path = Path()
        val fillPath = Path()

        val coordinates = points.mapIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value - minVal) / range) * (height * 0.75f) - (height * 0.12f)
            Offset(x, y)
        }

        if (coordinates.isNotEmpty()) {
            path.moveTo(coordinates[0].x, coordinates[0].y)
            fillPath.moveTo(coordinates[0].x, height)
            fillPath.lineTo(coordinates[0].x, coordinates[0].y)

            for (i in 0 until coordinates.size - 1) {
                val p0 = coordinates[i]
                val p1 = coordinates[i + 1]
                val cx = (p0.x + p1.x) / 2
                path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }

            fillPath.lineTo(coordinates.last().x, height)
            fillPath.close()

            // Fill gradient under curve
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, fillColor.copy(alpha = 0.01f)),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw end dot
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = coordinates.last()
            )
        }
    }
}

/**
 * Custom Canvas Donut Chart for spending category breakdown
 */
data class DonutSlice(val label: String, val value: Double, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp
) {
    val total = slices.sumOf { it.value }
    Canvas(modifier = modifier) {
        if (total <= 0.0) return@Canvas
        val canvasSize = minOf(size.width, size.height)
        val stroke = strokeWidth.toPx()
        val arcSize = Size(canvasSize - stroke, canvasSize - stroke)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)

        var startAngle = -90f
        val gapAngle = if (slices.size > 1) 2.5f else 0f

        slices.forEach { slice ->
            val sweepAngle = ((slice.value / total) * 360f).toFloat() - gapAngle
            if (sweepAngle > 0f) {
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + (gapAngle / 2),
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            startAngle += sweepAngle + gapAngle
        }
    }
}

/**
 * Editorial Helper for clean date titles
 */
fun formatEditorialDate(date: LocalDate, zone: String = "UTC"): String {
    val today = today(zone)
    return when (date) {
        today -> "Today"
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        else -> {
            val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            val dayOfWeek = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            "$dayOfWeek, $month ${date.day}, ${date.year}"
        }
    }
}
