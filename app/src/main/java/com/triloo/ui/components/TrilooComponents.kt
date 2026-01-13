package com.triloo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.with
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.triloo.ui.theme.*
import com.triloo.ui.theme.TrilooTheme

/**
 * Triloo Design System Components
 */

// BUTTONS

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TrilooButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = TrilooMotion.pressSpring,
        label = "buttonScale"
    )
    
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (style) {
        ButtonStyle.Primary -> colorScheme.primary to colorScheme.onPrimary
        ButtonStyle.Secondary -> colorScheme.secondary to colorScheme.onSecondary
        ButtonStyle.Tertiary -> Color.Transparent to colorScheme.primary
        ButtonStyle.Ghost -> Color.Transparent to colorScheme.onSurfaceVariant
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .height(52.dp),
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = colorScheme.surfaceVariant,
            disabledContentColor = colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                (fadeIn(
                    animationSpec = tween(
                        durationMillis = TrilooMotion.durationShort,
                        easing = TrilooMotion.easingStandard
                    )
                ) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(
                        durationMillis = TrilooMotion.durationShort,
                        easing = TrilooMotion.easingStandard
                    )
                )) with (fadeOut(
                    animationSpec = tween(
                        durationMillis = TrilooMotion.durationShort,
                        easing = TrilooMotion.easingExit
                    )
                ) + scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(
                        durationMillis = TrilooMotion.durationShort,
                        easing = TrilooMotion.easingExit
                    )
                ))
            },
            label = "buttonContent"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = contentColor,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

enum class ButtonStyle { Primary, Secondary, Tertiary, Ghost }

// FAB

@Composable
fun TrilooFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Add,
    contentDescription: String = "Add"
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = TrilooMotion.pressSpring,
        label = "fabScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isPressed) -3f else 0f,
        animationSpec = TrilooMotion.pressSpring,
        label = "fabRotation"
    )
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = colorScheme.primary.copy(alpha = 0.3f),
                spotColor = colorScheme.primary.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(18.dp),
        containerColor = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(26.dp)
        )
    }
}

// CARDS

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrilooCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = TrilooMotion.pressSpring,
        label = "cardScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 4.dp,
        animationSpec = tween(
            durationMillis = TrilooMotion.durationShort,
            easing = TrilooMotion.easingStandard
        ),
        label = "cardElevation"
    )
    val clickableModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick ?: {}
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .then(clickableModifier),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// CHIPS & BADGES

@Composable
fun TrilooChip(
    text: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    color: Color? = null,
    textColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val resolvedColor = color ?: colorScheme.secondaryContainer
    val resolvedTextColor = textColor ?: colorScheme.onSecondaryContainer
    val animatedColor by animateColorAsState(
        targetValue = resolvedColor,
        animationSpec = tween(
            durationMillis = TrilooMotion.durationShort,
            easing = TrilooMotion.easingStandard
        ),
        label = "chipColor"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = resolvedTextColor,
        animationSpec = tween(
            durationMillis = TrilooMotion.durationShort,
            easing = TrilooMotion.easingStandard
        ),
        label = "chipTextColor"
    )
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = animatedColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (emoji != null) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = animatedTextColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// AVATARS

@Composable
fun ParticipantAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    isOnline: Boolean = false,
    imageUrl: String? = null
) {
    Box(modifier = modifier) {
        // Avatar circle with gradient
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(CoralPrimary, CoralLight)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Online indicator
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Success)
            )
        }
    }
}

@Composable
fun AvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
    avatarSize: Dp = 32.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-12).dp)
    ) {
        names.take(maxVisible).forEachIndexed { index, name ->
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = when (index % 3) {
                                0 -> listOf(CoralPrimary, CoralLight)
                                1 -> listOf(TealSecondary, TealLight)
                                else -> listOf(GoldenAccent, GoldenLight)
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        if (names.size > maxVisible) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(Slate300),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${names.size - maxVisible}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate700,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// SECTION HEADER

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = CoralPrimary
                )
            }
        }
    }
}

// EMPTY STATE

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600,
            modifier = Modifier.fillMaxWidth(0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            TrilooButton(
                text = actionText,
                onClick = onAction
            )
        }
    }
}

// LOADING STATE

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "Загрузка..."
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = CoralPrimary,
            strokeWidth = 3.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600
        )
    }
}

@Preview(name = "Triloo Components", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun TrilooComponentsPreview() {
    TrilooTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrilooButton(text = "Primary", onClick = {})
            TrilooButton(text = "Secondary", onClick = {}, style = ButtonStyle.Secondary)
            TrilooCard {
                Text(text = "Карточка Triloo", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TrilooChip(text = "Чип", emoji = "🌍")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParticipantAvatar(name = "Аня")
                AvatarStack(names = listOf("А", "Б", "В", "Г"))
            }
            SectionHeader(title = "Пустое состояние")
            EmptyState(
                emoji = "🧭",
                title = "Ничего не найдено",
                subtitle = "Добавьте первую запись, чтобы увидеть список"
            )
        }
    }
}
