package com.example.smartskan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartskan.data.Categories
import com.example.smartskan.data.Expense
import com.example.smartskan.data.Repository
import com.example.smartskan.util.Ocr
import com.example.smartskan.util.ReceiptParser
import com.example.smartskan.util.formatDate
import com.example.smartskan.util.formatMoney
import com.example.smartskan.util.localToUtcDate
import com.example.smartskan.util.plainAmount
import com.example.smartskan.util.startOfMonth
import com.example.smartskan.util.utcDateToLocalNoon
import kotlinx.coroutines.launch

private data class ProductSummary(val barcode: String, val entries: List<Expense>) {
    val latest get() = entries.first()
    val previous get() = entries.getOrNull(1)
    val min get() = entries.minOf { it.amountCents }
    val max get() = entries.maxOf { it.amountCents }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingScreen(repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expenses by repo.expenses.items.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var viewingProduct by remember { mutableStateOf<String?>(null) }

    fun openNew(draft: Expense) {
        isNew = true
        editing = draft
    }

    val scanReceipt = rememberDocumentScanner(pageLimit = 1, includePdf = false) { result ->
        val uri = result.pages?.firstOrNull()?.imageUri ?: return@rememberDocumentScanner
        busy = true
        scope.launch {
            try {
                val parsed = ReceiptParser.parse(Ocr.readRows(context, uri))
                openNew(
                    Expense(
                        title = parsed.merchant ?: "Receipt",
                        amountCents = parsed.totalCents ?: 0,
                        date = parsed.dateMillis ?: System.currentTimeMillis(),
                        source = Expense.SOURCE_RECEIPT,
                    )
                )
                if (parsed.totalCents == null) toast(context, "Couldn't find the total, please enter it")
            } catch (e: Exception) {
                toast(context, "Couldn't read receipt: ${e.localizedMessage}")
            } finally {
                busy = false
            }
        }
    }

    fun scanProduct() = scanBarcode(context) { barcode ->
        val code = barcode.rawValue ?: return@scanBarcode
        val last = repo.expenses.items.value.firstOrNull { it.barcode == code }
        openNew(
            Expense(
                title = last?.title.orEmpty(),
                amountCents = last?.amountCents ?: 0,
                category = last?.category ?: Categories.GROCERIES,
                barcode = code,
                source = Expense.SOURCE_PRODUCT,
            )
        )
    }

    val products = remember(expenses) {
        expenses.filter { it.barcode != null }
            .groupBy { it.barcode!! }
            .map { (code, list) -> ProductSummary(code, list.sortedByDescending { it.date }) }
            .sortedByDescending { it.latest.date }
    }

    ScreenScaffold(
        title = "Spending",
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { menuOpen = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add") },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Scan receipt") }, leadingIcon = { Icon(Icons.Outlined.Receipt, null) },
                        onClick = { menuOpen = false; scanReceipt() })
                    DropdownMenuItem(text = { Text("Scan product barcode") }, leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, null) },
                        onClick = { menuOpen = false; scanProduct() })
                    DropdownMenuItem(text = { Text("Add manually") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menuOpen = false; openNew(Expense(title = "", amountCents = 0)) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expenses") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Products") })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val listPad = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 96.dp)
            when (tab) {
                0 -> if (expenses.isEmpty()) {
                    EmptyState(Icons.Outlined.Receipt, "Track your spending",
                        "Tap Add to scan a receipt (the shop, total and date are filled in for you), scan a product barcode, or add an expense by hand.")
                } else {
                    LazyColumn(contentPadding = listPad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SummaryCard(expenses) }
                        items(expenses, key = { it.id }) { e ->
                            ExpenseRow(e) { isNew = false; editing = e }
                        }
                    }
                }
                else -> if (products.isEmpty()) {
                    EmptyState(Icons.Outlined.LocalOffer, "Track product prices",
                        "Scan a product's barcode and enter its price. Scan it again next time you buy it to see how the price changes.")
                } else {
                    LazyColumn(contentPadding = listPad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(products, key = { it.barcode }) { p ->
                            ProductRow(p) { viewingProduct = p.barcode }
                        }
                    }
                }
            }
        }
    }

    editing?.let { expense ->
        key(expense.id) {
            ExpenseDialog(
                initial = expense,
                isNew = isNew,
                history = expense.barcode?.let { code ->
                    expenses.filter { it.barcode == code && it.id != expense.id }
                }.orEmpty(),
                onSave = { repo.saveExpense(it); editing = null },
                onDelete = { repo.deleteExpense(expense.id); editing = null },
                onDismiss = { editing = null },
            )
        }
    }

    viewingProduct?.let { code ->
        val product = products.firstOrNull { it.barcode == code }
        if (product == null) {
            viewingProduct = null
        } else {
            AlertDialog(
                onDismissRequest = { viewingProduct = null },
                title = { Text(product.latest.title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Barcode $code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Lowest ${formatMoney(product.min)} · Highest ${formatMoney(product.max)}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        product.entries.forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewingProduct = null; isNew = false; editing = e }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(formatDate(e.date))
                                Text(formatMoney(e.amountCents), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewingProduct = null
                        val last = product.latest
                        openNew(Expense(title = last.title, amountCents = last.amountCents, category = last.category,
                            barcode = code, source = Expense.SOURCE_PRODUCT))
                    }) { Text("Add new price") }
                },
                dismissButton = { TextButton(onClick = { viewingProduct = null }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun SummaryCard(expenses: List<Expense>) {
    val monthStart = startOfMonth(0)
    val lastMonthStart = startOfMonth(-1)
    val thisMonth = expenses.filter { it.date >= monthStart }
    val total = thisMonth.sumOf { it.amountCents }
    val lastTotal = expenses.filter { it.date in lastMonthStart until monthStart }.sumOf { it.amountCents }
    val byCategory = thisMonth.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .entries.sortedByDescending { it.value }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("This month", style = MaterialTheme.typography.labelLarge)
            Text(formatMoney(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${thisMonth.size} expense${if (thisMonth.size == 1) "" else "s"} · last month ${formatMoney(lastTotal)}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (byCategory.isNotEmpty() && total > 0) {
                Spacer(Modifier.height(12.dp))
                byCategory.take(4).forEach { (category, amount) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category, style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(amount), style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { amount.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    val icon: ImageVector = when (expense.source) {
        Expense.SOURCE_RECEIPT -> Icons.Outlined.Receipt
        Expense.SOURCE_PRODUCT -> Icons.Outlined.LocalOffer
        else -> Icons.Outlined.Edit
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${expense.category} · ${formatDate(expense.date)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMoney(expense.amountCents), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProductRow(product: ProductSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalOffer, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(product.latest.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Bought ${product.entries.size}× · ${formatMoney(product.min)}–${formatMoney(product.max)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(product.latest.amountCents), style = MaterialTheme.typography.titleMedium)
                product.previous?.let { prev ->
                    val diff = product.latest.amountCents - prev.amountCents
                    if (diff != 0L) {
                        val up = diff > 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = if (up) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Icon(if (up) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null,
                                Modifier.size(14.dp), tint = color)
                            Text(formatMoney(kotlin.math.abs(diff)), style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDialog(
    initial: Expense,
    isNew: Boolean,
    history: List<Expense>,
    onSave: (Expense) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var amountText by rememberSaveable { mutableStateOf(if (initial.amountCents > 0) plainAmount(initial.amountCents) else "") }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var date by rememberSaveable { mutableLongStateOf(initial.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    val cents = ReceiptParser.parseAmount(amountText)
    val valid = title.isNotBlank() && cents != null && cents > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New expense" else "Edit expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (initial.barcode != null) "Product name" else "Shop / description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = amountText.isNotEmpty() && cents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categories.all.forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) })
                    }
                }
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(date))
                }
                initial.barcode?.let {
                    Text("Barcode $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (history.isNotEmpty()) {
                    Text("Previous prices", style = MaterialTheme.typography.labelLarge)
                    history.take(5).forEach { h ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDate(h.date), style = MaterialTheme.typography.bodyMedium)
                            Text(formatMoney(h.amountCents), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(title = title.trim(), amountCents = cents ?: 0, category = category, date = date))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localToUtcDate(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = utcDateToLocalNoon(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state)
        }
    }
}
