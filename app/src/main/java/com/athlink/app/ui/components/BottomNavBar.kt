package com.athlink.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.athlink.app.ui.theme.AthlinkOrange

data class BottomNavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector, val route: String)

val playerNavItems = listOf(
    BottomNavItem("Home",    Icons.Filled.Home,    Icons.Outlined.Home,    "player_home"),
    BottomNavItem("Search",  Icons.Filled.Search,  Icons.Outlined.Search,  "player_search"),
    BottomNavItem("Academies", Icons.Filled.School, Icons.Outlined.School, "player_academies"),
    BottomNavItem("Chat",    Icons.Filled.Chat,    Icons.Outlined.ChatBubbleOutline, "player_chat"),
    BottomNavItem("Profile", Icons.Filled.Person,  Icons.Outlined.Person,  "player_profile")
)

val coachNavItems = listOf(
    BottomNavItem("Home",     Icons.Filled.Home,      Icons.Outlined.Home,       "coach_home"),
    BottomNavItem("Sessions", Icons.Filled.EventNote, Icons.Outlined.EventNote,  "coach_sessions"),
    BottomNavItem("Profile",  Icons.Filled.Person,    Icons.Outlined.Person,     "coach_profile")
)

val orgNavItems = listOf(
    BottomNavItem("Home",    Icons.Filled.Home,      Icons.Outlined.Home,      "org_home"),
    BottomNavItem("Events",  Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "org_events"),
    BottomNavItem("Profile", Icons.Filled.Person,    Icons.Outlined.Person,    "org_profile")
)

@Composable
fun AthlinkBottomBar(items: List<BottomNavItem>, currentRoute: String?, onItemClick: (String) -> Unit) {
    NavigationBar(
        modifier = Modifier.height(68.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onItemClick(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AthlinkOrange,
                    selectedTextColor = AthlinkOrange,
                    indicatorColor = AthlinkOrange.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
