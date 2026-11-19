package dev.lumora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.lumora.app.BuildConfig
import dev.lumora.app.data.AppSettings
import dev.lumora.app.media.MediaSnapshot
import dev.lumora.app.ui.theme.LocalAccent
import dev.lumora.app.ui.theme.LumoraColors

/** Callbacks from the settings sheet. */
data class SettingsActions(
    val onAdBlock: (Boolean) -> Unit,
    val onBackgroundPlay: (Boolean) -> Unit,
    val onSponsorBlock: (Boolean) -> Unit,
    val onAutoPip: (Boolean) -> Unit,
    val onDesktop: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
    val onClearData: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    blockedCount: Int,
    nowPlaying: MediaSnapshot,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var confirmClear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LumoraColors.Surface,
        contentColor = LumoraColors.OnSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Header(blockedCount)

            AnimatedVisibility(nowPlaying.hasMedia) {
                NowPlayingCard(nowPlaying, actions)
            }

            SectionLabel("Playback")
            SettingsGroup {
                ToggleRow(
                    icon = Icons.Rounded.Headphones,
                    title = "Background play",
                    subtitle = "Keep audio running with the screen off or app minimised",
                    checked = settings.backgroundPlayEnabled,
                    onCheckedChange = actions.onBackgroundPlay,
                )
                ToggleRow(
                    icon = Icons.Rounded.PictureInPictureAlt,
                    title = "Picture-in-picture",
                    subtitle = "Shrink full-screen video into a floating window when you leave",
                    checked = settings.autoPipEnabled,
                    onCheckedChange = actions.onAutoPip,
                )
            }

            SectionLabel("Blocking")
            SettingsGroup {
                ToggleRow(
                    icon = Icons.Rounded.Shield,
                    title = "Ad blocker",
                    subtitle = "Block ad servers & trackers, auto-skip video ads, hide promos",
                    checked = settings.adBlockEnabled,
                    onCheckedChange = actions.onAdBlock,
                )
                ToggleRow(
                    icon = Icons.Rounded.FastForward,
                    title = "SponsorBlock auto-skip",
                    subtitle = "Skip sponsor reads, self-promo, subscribe reminders & non-music parts",
                    checked = settings.sponsorBlockEnabled,
                    onCheckedChange = actions.onSponsorBlock,
                )
            }

            SectionLabel("Browsing")
            SettingsGroup {
                ToggleRow(
                    icon = Icons.Rounded.DesktopWindows,
                    title = "Desktop view",
                    subtitle = "Load the full desktop layout",
                    checked = settings.desktopMode,
                    onCheckedChange = { actions.onDesktop() },
                )
                ActionRow(Icons.Rounded.Share, "Share current page", actions.onShare)
                ActionRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open in browser", actions.onOpenInBrowser)
                ActionRow(Icons.Rounded.CleaningServices, "Clear cache & cookies", { confirmClear = true }, destructive = true)
            }

            Text(
                "Lumora ${BuildConfig.VERSION_NAME} · personal build",
                style = MaterialTheme.typography.labelSmall,
                color = LumoraColors.OnSurfaceMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = LumoraColors.SurfaceHigh,
            title = { Text("Clear browsing data?") },
            text = { Text("This signs you out of Google and removes cached pages, cookies and site storage.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    actions.onClearData()
                }) { Text("Clear", color = LocalAccent.current.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = LumoraColors.OnSurface) }
            },
        )
    }
}

@Composable
private fun Header(blockedCount: Int) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        AppLogo(size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Lumora", style = MaterialTheme.typography.titleLarge)
            Text("Settings", style = MaterialTheme.typography.bodyMedium, color = LumoraColors.OnSurfaceMuted)
        }
        Row(
            Modifier
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.14f))
                .border(1.dp, accent.primary.copy(alpha = 0.35f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Block, null, tint = accent.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "$blockedCount blocked",
                style = MaterialTheme.typography.labelMedium,
                color = accent.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NowPlayingCard(media: MediaSnapshot, actions: SettingsActions) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.primary.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LumoraColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = LumoraColors.OnSurfaceMuted)
            if (media.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = media.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (media.isAd) "Skipping ad…" else media.title.ifBlank { "Now playing" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.artist.isNotBlank()) {
                Text(
                    media.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = LumoraColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = actions.onPrevious) { Icon(Icons.Rounded.SkipPrevious, "Previous") }
        FilledIconButton(
            onClick = actions.onPlayPause,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent.primary),
        ) {
            Icon(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White)
        }
        IconButton(onClick = actions.onNext) { Icon(Icons.Rounded.SkipNext, "Next") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LumoraColors.OnSurfaceMuted,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LumoraColors.SurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (checked) accent.primary else LumoraColors.OnSurfaceMuted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LumoraColors.OnSurfaceMuted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent.primary,
                uncheckedThumbColor = LumoraColors.OnSurfaceMuted,
                uncheckedTrackColor = LumoraColors.Background,
                uncheckedBorderColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit, destructive: Boolean = false) {
    val color = if (destructive) LumoraColors.Crimson else LumoraColors.OnSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (destructive) LumoraColors.Crimson else LumoraColors.OnSurfaceMuted)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
    }
}
