package com.personalmentor.app.presentation.macro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.macro.MacroCatalog
import com.personalmentor.app.domain.macro.MacroItem
import com.personalmentor.app.domain.macro.MacroSection
import com.personalmentor.app.domain.macro.MacroTemplate
import com.personalmentor.app.domain.macro.MacroTemplates

/** Navy top bar of the macro list: title with count, filter, search and a menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosTopBar(
    state: MacrosUiState,
    onBack: () -> Unit,
    onFilter: (MacroFilter) -> Unit,
    onToggleSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onCollapseAll: (Boolean) -> Unit,
    onShowLog: () -> Unit,
) {
    var filterMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MacroColors.Navy,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        title = {
            if (state.searching) {
                TextField(
                    value = state.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text("Search macros", color = Color.White.copy(alpha = 0.6f)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                    ),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Macros", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(10.dp))
                    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 2.dp)) {
                        Text("${state.macros.size}", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { filterMenu = true }) { Icon(Icons.Default.FilterList, contentDescription = "Filter") }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    MacroFilter.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = { onFilter(f); filterMenu = false })
                    }
                }
            }
            IconButton(onClick = onToggleSearch) {
                Icon(if (state.searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(text = { Text("Activity log") }, onClick = { onShowLog(); moreMenu = false })
                    DropdownMenuItem(text = { Text("Collapse all") }, onClick = { onCollapseAll(true); moreMenu = false })
                    DropdownMenuItem(text = { Text("Expand all") }, onClick = { onCollapseAll(false); moreMenu = false })
                }
            }
        },
    )
}

@Composable
fun MacrosTab(
    state: MacrosUiState,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onFilter: (MacroFilter) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onCollapseAll: (Boolean) -> Unit,
    onEnabled: (Long, Boolean) -> Unit,
    onCategoryEnabled: (String, Boolean) -> Unit,
    onFavorite: (Long) -> Unit,
) {
    val groups = state.groups
    val allCollapsed = groups.isNotEmpty() && groups.all { it.first in state.collapsed }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MacroFilter.entries) { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { onFilter(f) },
                            label = { Text(f.label) },
                            leadingIcon = if (f == MacroFilter.FAVORITES) {
                                { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val on = state.macros.count { it.enabled }
                    Text(
                        "$on ON · ${groups.size} ${if (groups.size == 1) "CATEGORY" else "CATEGORIES"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onCollapseAll(!allCollapsed) }) { Text(if (allCollapsed) "Expand" else "Collapse") }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        if (state.macros.isEmpty()) "No macros yet. Tap + to create one, or pick a template." else "No macros match this filter.",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            groups.forEach { (category, macros) ->
                item(key = "cat-$category") {
                    CategoryHeader(
                        name = category,
                        count = macros.size,
                        collapsed = category in state.collapsed,
                        enabled = macros.any { it.enabled },
                        onToggleCollapsed = { onToggleCollapsed(category) },
                        onEnabled = { onCategoryEnabled(category, it) },
                    )
                }
                if (category !in state.collapsed) {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            running = macro.id in state.running,
                            onClick = { onOpen(macro.id) },
                            onEnabled = { onEnabled(macro.id, it) },
                            onFavorite = { onFavorite(macro.id) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNew,
            containerColor = MacroColors.Navy,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "New macro") }
    }
}

@Composable
private fun CategoryHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    enabled: Boolean,
    onToggleCollapsed: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleCollapsed).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MacroColors.Navy)
        Spacer(Modifier.size(12.dp))
        Text(name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(10.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 2.dp)) {
            Text("$count", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.weight(1f))
        Icon(if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = if (collapsed) "Expand" else "Collapse")
        Spacer(Modifier.size(8.dp))
        Switch(checked = enabled, onCheckedChange = onEnabled)
    }
}

@Composable
private fun MacroCard(
    macro: Macro,
    running: Boolean,
    onClick: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onFavorite: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MacroColors.NavyDark).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                Icon(if (macro.favorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Favorite", tint = Color.White)
            }
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(macro.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val status = when {
                    running -> "Running…"
                    macro.lastError != null -> "Failed ${relativeTime(macro.lastRunAt)} · ${macro.lastError}"
                    else -> "Ran ${relativeTime(macro.lastRunAt)} · Edited ${relativeTime(macro.editedAt)}"
                }
                Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = macro.enabled, onCheckedChange = onEnabled)
        }
        ItemsRow(MacroSection.TRIGGER, macro.triggers)
        ItemsRow(MacroSection.ACTION, macro.actions)
        ItemsRow(MacroSection.CONSTRAINT, macro.constraints)
    }
}

/** One coloured strip of a macro card (red triggers, blue actions, green constraints). */
@Composable
private fun ItemsRow(section: MacroSection, items: List<MacroItem>) {
    if (items.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().background(MacroColors.body(section)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.take(3).forEach { item ->
            Box(Modifier.padding(end = 6.dp).size(28.dp).clip(CircleShape).background(MacroColors.bar(section)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(MacroCatalog.find(item.type)?.category), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            items.joinToString(", ") { MacroCatalog.summary(it) },
            color = Color(0xFF1B1B1F),
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TemplatesTab(onAdd: (MacroTemplate) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(MacroTemplates.all) { template ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 4.dp),
            ) {
                Column(Modifier.fillMaxWidth().background(MacroColors.NavyDark).padding(14.dp)) {
                    Text(template.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(template.description, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                ItemsRow(MacroSection.TRIGGER, template.macro.triggers)
                ItemsRow(MacroSection.ACTION, template.macro.actions)
                ItemsRow(MacroSection.CONSTRAINT, template.macro.constraints)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onAdd(template) }) { Text("Add to my macros") }
                }
            }
        }
    }
}

@Composable
fun ActivityLogDialog(log: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity log") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                if (log.isEmpty()) item { Text("Nothing has run yet.") }
                items(log.asReversed()) { line -> Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
