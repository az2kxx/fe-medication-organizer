package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.resources.Res
import com.elcomp.medicationorganizer.resources.navigation_history
import com.elcomp.medicationorganizer.resources.navigation_medications
import com.elcomp.medicationorganizer.resources.navigation_organizer
import com.elcomp.medicationorganizer.resources.navigation_today
import com.elcomp.medicationorganizer.resources.tab_history_icon
import com.elcomp.medicationorganizer.resources.tab_medications_icon
import com.elcomp.medicationorganizer.resources.tab_organizer_icon
import com.elcomp.medicationorganizer.resources.tab_today_icon
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal enum class MainTab(
    val label: StringResource,
    val icon: DrawableResource,
    val testTag: String,
) {
    Today(Res.string.navigation_today, Res.drawable.tab_today_icon, "tab-today"),
    Organizer(Res.string.navigation_organizer, Res.drawable.tab_organizer_icon, "tab-organizer"),
    Medications(Res.string.navigation_medications, Res.drawable.tab_medications_icon, "tab-medications"),
    History(Res.string.navigation_history, Res.drawable.tab_history_icon, "tab-history"),
}

/** Place in a separate bottom slot. This component applies horizontal and bottom safe drawing insets. */
@Composable
internal fun AppBottomNavigation(selected: MainTab, textScale: Int, onSelect: (MainTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val fontScale = LocalDensity.current.fontScale * (textScale / 100f)
            val columns = if (maxWidth / fontScale < 320.dp) 2 else 4

            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MainTab.entries.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { tab ->
                            MainTabItem(
                                tab = tab,
                                selected = selected == tab,
                                onClick = { onSelect(tab) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val appearance = if (selected) {
        Modifier
            .background(colors.secondaryContainer)
            .border(width = 2.dp, color = colors.primary, shape = shape)
    } else {
        Modifier
    }
    val foreground = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .then(appearance)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .testTag(tab.testTag)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(tab.label),
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
