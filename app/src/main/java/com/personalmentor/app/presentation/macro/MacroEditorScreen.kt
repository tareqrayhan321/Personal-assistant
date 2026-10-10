package com.personalmentor.app.presentation.macro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmentor.app.domain.macro.ItemDef
import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.macro.MacroCatalog
import com.personalmentor.app.domain.macro.MacroItem
import com.personalmentor.app.domain.macro.MacroSection
import com.personalmentor.app.domain.macro.ParamDef
import com.personalmentor.app.domain.macro.ParamKind

/** Which page of the editor is showing: the macro itself, a category list, or the items of a category. */
private sealed interface Page {
    data object Editor : Page
    data class Categories(val section: MacroSection) : Page
    data class Items(val section: MacroSection, val category: String) : Page
}

private fun Page.encode(): String = when (this) {
    Page.Editor -> "editor"
    is Page.Categories -> "cat|${section.name}"
    is Page.Items -> "items|${section.name}|$category"
}

private fun decodePage(raw: String): Page {
    val parts = raw.split('|')
    return when (parts.firstOrNull()) {
        "cat" -> Page.Categories(MacroSection.valueOf(parts[1]))
        "items" -> Page.Items(MacroSection.valueOf(parts[1]), parts.drop(2).joinToString("|"))
        else -> Page.Editor
    }
}

/** An item being added (index null) or edited (index set) in the parameter dialog. */
private data class Config(val section: MacroSection, val def: ItemDef, val item: MacroItem, val index: Int?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    onClose: () -> Unit,
    viewModel: MacroEditorViewModel = hiltViewModel(),
) {
    val macro by viewModel.macro.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pageCode by rememberSaveable { mutableStateOf(Page.Editor.encode()) }
    val page = decodePage(pageCode)
    var config by remember { mutableStateOf<Config?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    fun close() {
        viewModel.persist()
        onClose()
    }

    BackHandler {
        when (page) {
            Page.Editor -> close()
            is Page.Categories -> pageCode = Page.Editor.encode()
            is Page.Items -> pageCode = Page.Categories(page.section).encode()
        }
    }

    when (page) {
        Page.Editor -> EditorPage(
            macro = macro,
            categories = viewModel.categories,
            snackbar = snackbar,
            onBack = ::close,
            onName = viewModel::rename,
            onNotes = viewModel::setNotes,
            onCategory = viewModel::setCategory,
            onAdd = { section -> pageCode = Page.Categories(section).encode() },
            onEdit = { section, index ->
                val item = macro.items(section)[index]
                MacroCatalog.find(item.type)?.let { config = Config(section, it, item, index) }
            },
            onRemove = viewModel::removeItem,
            onTest = viewModel::testRun,
            onDelete = { viewModel.delete(); onClose() },
            onAddVariable = viewModel::addVariable,
            onRemoveVariable = viewModel::removeVariable,
        )
        is Page.Categories -> CategoryPage(
            section = page.section,
            onBack = { pageCode = Page.Editor.encode() },
            onCategory = { pageCode = Page.Items(page.section, it).encode() },
            onItem = { def -> config = Config(page.section, def, MacroCatalog.newItem(def), null) },
        )
        is Page.Items -> ItemsPage(
            section = page.section,
            category = page.category,
            onBack = { pageCode = Page.Categories(page.section).encode() },
            onItem = { def -> config = Config(page.section, def, MacroCatalog.newItem(def), null) },
        )
    }

    config?.let { c ->
        ItemConfigDialog(
            config = c,
            onDismiss = { config = null },
            onConfirm = { item ->
                if (c.index == null) {
                    viewModel.addItem(c.section, item)
                    pageCode = Page.Editor.encode()
                } else {
                    viewModel.replaceItem(c.section, c.index, item)
                }
                config = null
            },
        )
    }
}

// ---------------------------------------------------------------- editor page

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorPage(
    macro: Macro,
    categories: List<String>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onNotes: (String) -> Unit,
    onCategory: (String) -> Unit,
    onAdd: (MacroSection) -> Unit,
    onEdit: (MacroSection, Int) -> Unit,
    onRemove: (MacroSection, Int) -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onAddVariable: (String, String) -> String?,
    onRemoveVariable: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MacroColors.Navy,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    TextField(
                        value = macro.name,
                        onValueChange = onName,
                        singleLine = true,
                        placeholder = { Text("Enter macro name", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f)) },
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.White,
                        ),
                    )
                },
                actions = {
                    IconButton(onClick = { notesDialog = true }) { Icon(Icons.Default.Description, contentDescription = "Notes") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Category: ${macro.category}") }, onClick = { menu = false; categoryDialog = true })
                            DropdownMenuItem(text = { Text("Delete macro") }, onClick = { menu = false; deleteDialog = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onTest, containerColor = Color.White, contentColor = MacroColors.Navy, shape = CircleShape) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run now")
            }
        },
        bottomBar = { VariablesPanel(macro, onAddVariable, onRemoveVariable) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacroSection.entries.forEach { section ->
                SectionCard(
                    section = section,
                    items = macro.items(section),
                    onAdd = { onAdd(section) },
                    onEdit = { onEdit(section, it) },
                    onRemove = { onRemove(section, it) },
                )
            }
            Spacer(Modifier.size(72.dp))
        }
    }

    if (notesDialog) {
        var text by remember { mutableStateOf(macro.notes) }
        AlertDialog(
            onDismissRequest = { notesDialog = false },
            title = { Text("Notes") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { TextButton(onClick = { onNotes(text); notesDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { notesDialog = false }) { Text("Cancel") } },
        )
    }
    if (categoryDialog) {
        var text by remember { mutableStateOf(macro.category) }
        AlertDialog(
            onDismissRequest = { categoryDialog = false },
            title = { Text("Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    categories.forEach { c -> Text(c, modifier = Modifier.fillMaxWidth().clickable { text = c }.padding(vertical = 6.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { onCategory(text); categoryDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text("Cancel") } },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete macro?") },
            text = { Text("\"${macro.name.ifBlank { "Untitled macro" }}\" will be removed.") },
            confirmButton = { TextButton(onClick = { deleteDialog = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } },
        )
    }
}

/** Red / blue / green card: coloured header with count and + button, lighter body with the items. */
@Composable
private fun SectionCard(
    section: MacroSection,
    items: List<MacroItem>,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MacroColors.header(section)).padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(12.dp))
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.25f)).padding(horizontal = 12.dp, vertical = 2.dp)) {
                Text("${items.size}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            ) { Icon(Icons.Default.Add, contentDescription = "Add ${section.singular.lowercase()}", tint = Color.White) }
        }
        Column(Modifier.fillMaxWidth().background(MacroColors.body(section))) {
            if (items.isEmpty()) {
                Text(
                    "No ${section.title}",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 22.sp,
                    color = Color(0xFF1B1B1F),
                )
            }
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(index) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(MacroColors.bar(section)), contentAlignment = Alignment.Center) {
                        Icon(categoryIcon(MacroCatalog.find(item.type)?.category), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(MacroCatalog.summary(item), modifier = Modifier.weight(1f), color = Color(0xFF1B1B1F), fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemove(index) }) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF1B1B1F)) }
                }
            }
        }
    }
}

/** Teal strip at the bottom; tap to open the macro's local variables. */
@Composable
private fun VariablesPanel(macro: Macro, onAdd: (String, String) -> String?, onRemove: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MacroColors.Teal)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(24.dp))
            Text("Local Variables", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { addDialog = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, contentDescription = "Add variable", tint = Color.White) }
        }
        if (expanded) {
            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (macro.variables.isEmpty()) Text("No variables. Use {name} in action texts.", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp))
                macro.variables.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${v.name} = ${v.value}", color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(v.name) }) { Icon(Icons.Default.Close, contentDescription = "Remove variable", tint = Color.White) }
                    }
                }
            }
        }
    }
    if (addDialog) {
        var name by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("New variable") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } })
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Start value") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = onAdd(name, value)
                    if (error == null) addDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- picker pages

/** Coloured top bar used by the "Add Trigger / Action / Constraint" pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScaffold(
    section: MacroSection,
    title: String,
    onBack: () -> Unit,
    searching: Boolean,
    query: String,
    onSearchToggle: () -> Unit,
    onQuery: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    var help by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MacroColors.bar(section),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = onQuery, singleLine = true,
                            placeholder = { Text("Search", color = Color.White.copy(alpha = 0.6f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White,
                            ),
                        )
                    } else {
                        Text(title, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onSearchToggle) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search") }
                    IconButton(onClick = { help = true }) { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help") }
                },
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text(section.title) },
            text = {
                Text(
                    when (section) {
                        MacroSection.TRIGGER -> "A trigger starts the macro when something happens. Several triggers mean any one of them starts it."
                        MacroSection.ACTION -> "Actions run one after another when the macro starts. Launching apps or web pages only works while this app is on screen."
                        MacroSection.CONSTRAINT -> "Constraints must all be true at the moment a trigger fires, otherwise the macro is skipped."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun CategoryPage(section: MacroSection, onBack: () -> Unit, onCategory: (String) -> Unit, onItem: (ItemDef) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    PickerScaffold(
        section = section,
        title = "Add ${section.singular}",
        onBack = onBack,
        searching = searching,
        query = query,
        onSearchToggle = { searching = !searching; query = "" },
        onQuery = { query = it },
    ) {
        if (searching && query.isNotBlank()) {
            LazyColumn {
                items(MacroCatalog.search(section, query)) { def ->
                    PickerRow(section, categoryIcon(def.category), def.label, def.category) { onItem(def) }
                }
            }
        } else {
            LazyColumn {
                items(MacroCatalog.categories(section)) { category ->
                    PickerRow(section, categoryIcon(category), category, null) { onCategory(category) }
                }
            }
        }
    }
}

@Composable
private fun ItemsPage(section: MacroSection, category: String, onBack: () -> Unit, onItem: (ItemDef) -> Unit) {
    PickerScaffold(
        section = section, title = category, onBack = onBack,
        searching = false, query = "", onSearchToggle = {}, onQuery = {},
    ) {
        LazyColumn {
            items(MacroCatalog.items(section, category)) { def ->
                PickerRow(section, categoryIcon(def.category), def.label, def.description) { onItem(def) }
            }
        }
    }
}

@Composable
private fun PickerRow(section: MacroSection, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MacroColors.bar(section).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1B1B1F))
        }
        Spacer(Modifier.size(18.dp))
        Column {
            Text(title, fontSize = 20.sp)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- parameters dialog

@Composable
private fun ItemConfigDialog(config: Config, onDismiss: () -> Unit, onConfirm: (MacroItem) -> Unit) {
    val values = remember(config) { mutableStateOf(config.item.params) }
    var error by remember(config) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.def.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(config.def.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                config.def.params.forEach { p ->
                    ParamField(p, values.value[p.key].orEmpty()) { values.value = values.value + (p.key to it) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val item = MacroItem(config.def.type, values.value)
                val problem = MacroCatalog.validate(item, config.section)
                if (problem == null) onConfirm(item) else error = problem
            }) { Text(if (config.index == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ParamField(param: ParamDef, value: String, onChange: (String) -> Unit) {
    if (param.kind == ParamKind.CHOICE) {
        Column {
            Text(param.label, style = MaterialTheme.typography.labelMedium)
            param.options.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selected = value == option, role = Role.RadioButton, onClick = { onChange(option) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == option, onClick = null)
                    Text(option, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(param.label + if (param.optional) " (optional)" else "") },
            singleLine = param.kind != ParamKind.TEXT,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (param.kind) {
                    ParamKind.NUMBER -> KeyboardType.Number
                    ParamKind.URL -> KeyboardType.Uri
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
