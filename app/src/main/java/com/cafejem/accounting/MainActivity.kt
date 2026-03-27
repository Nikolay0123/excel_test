package com.cafejem.accounting

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cafejem.accounting.data.AccountingRepository
import com.cafejem.accounting.data.AppDatabase
import com.cafejem.accounting.data.Guest
import com.cafejem.accounting.data.GuestSummary
import com.cafejem.accounting.data.MealType
import com.cafejem.accounting.data.MonthlyExpense
import com.cafejem.accounting.data.MonthlyFinance
import com.cafejem.accounting.data.PaymentType
import com.cafejem.accounting.export.ExcelExporter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel> {
        val db = AppDatabase.create(this)
        val repo = AccountingRepository(db.dao())
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AppViewModel(repo) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { MainScreen(viewModel) } }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val month by viewModel.currentMonth.collectAsStateWithLifecycle()
    val guests by viewModel.guests.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val finance by viewModel.finance.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val exporter = remember { ExcelExporter() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Гости", "Ввод", "Финансы", "Итоги")

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    exporter.exportMonth(
                        context = context,
                        targetUri = uri,
                        month = month,
                        entries = entries,
                        ratesWithoutVat = viewModel.ratesMap(),
                        expenses = expenses,
                        finance = finance
                    )
                    Toast.makeText(context, "Экспорт завершен", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Cafe Jem: учет кухни") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(12.dp)
        ) {
            MonthSelector(month = month, onChange = viewModel::setMonth)
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            Spacer(Modifier.height(8.dp))
            when (selectedTab) {
                0 -> GuestsTab(guests = guests, onAddGuest = viewModel::addGuest)
                1 -> EntryTab(
                    guests = guests,
                    onAddEntry = viewModel::addEntry,
                    onAddEntriesForRange = viewModel::addEntriesForRange
                )
                2 -> FinanceTab(
                    onUpdateRate = viewModel::updateRate,
                    onUpdateSetting = viewModel::updateFinanceSetting,
                    onAddExpense = viewModel::addExpense,
                    expenses = expenses
                )
                else -> SummaryTab(summary = summary, finance = finance, onExport = {
                    exportLauncher.launch("CafeJem-$month.xls")
                })
            }
        }
    }
}

@Composable
private fun MonthSelector(month: String, onChange: (String) -> Unit) {
    var value by remember(month) { mutableStateOf(month) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Месяц YYYY-MM") })
        Button(onClick = { onChange(value) }) { Text("Применить") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestsTab(guests: List<Guest>, onAddGuest: (String, String, PaymentType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf(PaymentType.WITH_VAT) }
    val trimmedQuery = search.trim()
    val filteredGuests = guests.filter {
        extractSurname(it.name).contains(trimmedQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("ФИО/Организация") })
        OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Комната/Описание") })
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            TextField(
                modifier = Modifier.menuAnchor(),
                value = payment.asLabel(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Тип оплаты") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PaymentType.entries.forEach {
                    DropdownMenuItem(text = { Text(it.asLabel()) }, onClick = { payment = it; expanded = false })
                }
            }
        }
        Button(onClick = {
            if (name.isNotBlank()) {
                onAddGuest(name.trim(), room.trim(), payment)
                name = ""
                room = ""
            }
        }) { Text("Добавить гостя") }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Поиск гостя по фамилии") },
            modifier = Modifier.fillMaxWidth()
        )
        GuestSuggestions(
            query = search,
            guests = guests,
            onPick = { picked -> search = picked.name }
        )
        filteredGuests.forEach { guest ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(guest.name)
                    Text("${guest.roomOrOrg} | ${guest.paymentType.asLabel()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTab(
    guests: List<Guest>,
    onAddEntry: (Long, Int, MealType, PaymentType, Int) -> Unit,
    onAddEntriesForRange: (Long, Int, Int, MealType, PaymentType, Int) -> Unit
) {
    var selectedGuestId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var startDay by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var endDay by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var breakfastPortions by remember { mutableStateOf("") }
    var lunchPortions by remember { mutableStateOf("") }
    var dinnerPortions by remember { mutableStateOf("") }

    // false = питание в цене номера (без НДС), true = доп. услуга (НДС 22%)
    var isExtraService by remember { mutableStateOf(false) }

    var guestExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val trimmedQuery = search.trim()
    val filteredGuests = guests.filter {
        extractSurname(it.name).contains(trimmedQuery, ignoreCase = true)
    }
    val selectedGuest = guests.firstOrNull { it.id == selectedGuestId }
    val paymentTypeForMeals = when (selectedGuest?.paymentType) {
        PaymentType.CASH -> PaymentType.CASH
        else -> if (isExtraService) PaymentType.WITH_VAT else PaymentType.WITHOUT_VAT
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Поиск гостя по фамилии") },
            modifier = Modifier.fillMaxWidth()
        )
        GuestSuggestions(
            query = search,
            guests = guests,
            onPick = { picked ->
                selectedGuestId = picked.id
                search = picked.name
            }
        )
        ExposedDropdownMenuBox(
            expanded = guestExpanded,
            onExpandedChange = { guestExpanded = !guestExpanded }
        ) {
            TextField(
                modifier = Modifier.menuAnchor(),
                value = selectedGuest?.name ?: "Выберите гостя",
                onValueChange = {},
                readOnly = true,
                label = { Text("Гость") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = guestExpanded) }
            )
            androidx.compose.material3.DropdownMenu(expanded = guestExpanded, onDismissRequest = { guestExpanded = false }) {
                filteredGuests.forEach {
                    DropdownMenuItem(text = { Text(it.name) }, onClick = { selectedGuestId = it.id; guestExpanded = false })
                }
            }
        }

        Text("Добавить питание за период")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = startDay, onValueChange = { startDay = it }, label = { Text("С дня") })
            OutlinedTextField(value = endDay, onValueChange = { endDay = it }, label = { Text("По день") })
        }

        Text("Выбор: питание в цене номера / доп. услуга")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { isExtraService = false },
                enabled = selectedGuest?.paymentType != PaymentType.CASH
            ) {
                Text("В цене номера")
            }
            Button(
                onClick = { isExtraService = true },
                enabled = selectedGuest?.paymentType != PaymentType.CASH
            ) {
                Text("Доп. услуга")
            }
        }

        OutlinedTextField(
            value = breakfastPortions,
            onValueChange = { breakfastPortions = it },
            label = { Text("Завтрак (порции, пусто=не нужно)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = lunchPortions,
            onValueChange = { lunchPortions = it },
            label = { Text("Обед (порции, пусто=не нужно)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dinnerPortions,
            onValueChange = { dinnerPortions = it },
            label = { Text("Ужин (порции, пусто=не нужно)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            val from = startDay.toIntOrNull()
            val to = endDay.toIntOrNull()
            val b = breakfastPortions.toIntOrNull()
            val l = lunchPortions.toIntOrNull()
            val d = dinnerPortions.toIntOrNull()

            if (selectedGuestId != null && from != null && to != null && from in 1..31 && to in from..31) {
                var addedAny = false
                if (b != null && b > 0) {
                    onAddEntriesForRange(selectedGuestId!!, from, to, MealType.BREAKFAST, paymentTypeForMeals, b)
                    addedAny = true
                }
                if (l != null && l > 0) {
                    onAddEntriesForRange(selectedGuestId!!, from, to, MealType.LUNCH, paymentTypeForMeals, l)
                    addedAny = true
                }
                if (d != null && d > 0) {
                    onAddEntriesForRange(selectedGuestId!!, from, to, MealType.DINNER, paymentTypeForMeals, d)
                    addedAny = true
                }
                if (addedAny) {
                    Toast.makeText(context, "Период добавлен", Toast.LENGTH_SHORT).show()
                    breakfastPortions = ""
                    lunchPortions = ""
                    dinnerPortions = ""
                }
            }
        }) { Text("Добавить за период") }
    }
}

@Composable
private fun FinanceTab(
    onUpdateRate: (MealType, Double) -> Unit,
    onUpdateSetting: (Double, Double) -> Unit,
    onAddExpense: (String, String, Double) -> Unit,
    expenses: List<MonthlyExpense>
) {
    var breakfast by remember { mutableStateOf("800") }
    var lunch by remember { mutableStateOf("1000") }
    var dinner by remember { mutableStateOf("850") }
    var vat by remember { mutableStateOf("20") }
    var tax by remember { mutableStateOf("15") }
    var expenseName by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("Наличные") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ставки")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = breakfast, onValueChange = { breakfast = it }, label = { Text("Завтрак") })
            OutlinedTextField(value = lunch, onValueChange = { lunch = it }, label = { Text("Обед") })
            OutlinedTextField(value = dinner, onValueChange = { dinner = it }, label = { Text("Ужин") })
        }
        Button(onClick = {
            breakfast.toDoubleOrNull()?.let { onUpdateRate(MealType.BREAKFAST, it) }
            lunch.toDoubleOrNull()?.let { onUpdateRate(MealType.LUNCH, it) }
            dinner.toDoubleOrNull()?.let { onUpdateRate(MealType.DINNER, it) }
        }) { Text("Сохранить ставки") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = vat, onValueChange = { vat = it }, label = { Text("НДС %") })
            OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("Налог %") })
        }
        Button(onClick = {
            val v = vat.toDoubleOrNull()
            val t = tax.toDoubleOrNull()
            if (v != null && t != null) onUpdateSetting(v, t)
        }) { Text("Сохранить налоги") }

        Text("Издержки")
        OutlinedTextField(value = expenseName, onValueChange = { expenseName = it }, label = { Text("Статья") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = expenseAmount, onValueChange = { expenseAmount = it }, label = { Text("Сумма") })
            OutlinedTextField(value = channel, onValueChange = { channel = it }, label = { Text("Канал: Наличные/Безнал") })
        }
        Button(onClick = {
            val amount = expenseAmount.toDoubleOrNull()
            if (expenseName.isNotBlank() && amount != null) {
                val normalized = if (channel.lowercase(Locale.getDefault()).contains("без")) "bank" else "cash"
                onAddExpense(expenseName.trim(), normalized, amount)
                expenseName = ""
                expenseAmount = ""
            }
        }) { Text("Добавить издержку") }

        expenses.forEach {
            Text("${it.name}: ${it.amount} (${it.paymentChannel.asPaymentChannelLabel()})")
        }
    }
}

@Composable
private fun SummaryTab(summary: List<GuestSummary>, finance: MonthlyFinance?, onExport: () -> Unit) {
    val breakfast = summary.sumOf { it.breakfastCount }
    val lunch = summary.sumOf { it.lunchCount }
    val dinner = summary.sumOf { it.dinnerCount }
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Завтраков: $breakfast")
        Text("Обедов: $lunch")
        Text("Ужинов: $dinner")
        finance?.let {
            Text("Выручка: ${it.revenueTotal}")
            Text("Прибыль: ${it.profit}")
            Text("Остаток наличных: ${it.cashLeft}")
            Text("Остаток на счете: ${it.bankLeft}")
        }
        Button(onClick = onExport) { Text("Экспорт в XLS") }
    }
}

private fun PaymentType.asLabel(): String = when (this) {
    PaymentType.WITH_VAT -> "С НДС"
    PaymentType.WITHOUT_VAT -> "Без НДС"
    PaymentType.CASH -> "Наличные"
}

private fun MealType.asLabel(): String = when (this) {
    MealType.BREAKFAST -> "Завтрак"
    MealType.LUNCH -> "Обед"
    MealType.DINNER -> "Ужин"
}

private fun String.asPaymentChannelLabel(): String = if (this == "bank") "Безнал" else "Наличные"

@Composable
private fun GuestSuggestions(
    query: String,
    guests: List<Guest>,
    onPick: (Guest) -> Unit
) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val suggestions = guests
        .filter { extractSurname(it.name).contains(trimmed, ignoreCase = true) }
        .take(8)
    if (suggestions.isEmpty()) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Варианты:", style = MaterialTheme.typography.bodySmall)
            suggestions.forEach { guest ->
                Button(
                    onClick = { onPick(guest) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(guest.name)
                }
            }
        }
    }
}

private fun extractSurname(fullName: String): String {
    val first = fullName.trim().split(Regex("\\s+")).firstOrNull() ?: ""
    return first
}
