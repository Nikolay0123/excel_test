package com.cafejem.accounting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cafejem.accounting.data.AccountingRepository
import com.cafejem.accounting.data.Guest
import com.cafejem.accounting.data.FinanceSetting
import com.cafejem.accounting.data.GuestSummary
import com.cafejem.accounting.data.MealEntry
import com.cafejem.accounting.data.MonthlyExpense
import com.cafejem.accounting.data.MonthlyFinance
import com.cafejem.accounting.data.MealType
import com.cafejem.accounting.data.PaymentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repository: AccountingRepository
) : ViewModel() {

    private val currentMonthFlow = MutableStateFlow(LocalDate.now().withDayOfMonth(1).toString().take(7))
    val currentMonth: StateFlow<String> = currentMonthFlow

    val guests: StateFlow<List<Guest>> = repository.guestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entries: StateFlow<List<MealEntry>> = currentMonthFlow.flatMapLatest { repository.entriesFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<List<GuestSummary>> = currentMonthFlow.flatMapLatest { repository.summaryFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<MonthlyExpense>> = currentMonthFlow.flatMapLatest { repository.expensesFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _finance = MutableStateFlow<MonthlyFinance?>(null)
    val finance: StateFlow<MonthlyFinance?> = _finance
    private val _rates = MutableStateFlow<Map<MealType, Double>>(emptyMap())
    val rates: StateFlow<Map<MealType, Double>> = _rates
    private val _financeSetting = MutableStateFlow(FinanceSetting())
    val financeSetting: StateFlow<FinanceSetting> = _financeSetting

    init {
        viewModelScope.launch {
            repository.ensureDefaultRates()
            _rates.value = repository.ratesMap()
            _financeSetting.value = repository.financeSetting()
            refreshFinance()
        }
    }

    fun setMonth(value: String) {
        currentMonthFlow.value = value
        viewModelScope.launch { refreshFinance() }
    }

    fun addGuest(name: String, roomOrOrg: String, paymentType: PaymentType) {
        viewModelScope.launch {
            repository.addGuest(name, roomOrOrg, paymentType)
            refreshFinance()
        }
    }

    fun updateGuest(id: Long, name: String, roomOrOrg: String, paymentType: PaymentType) {
        viewModelScope.launch {
            repository.updateGuest(id, name, roomOrOrg, paymentType)
            refreshFinance()
        }
    }

    fun deleteGuest(id: Long) {
        viewModelScope.launch {
            repository.deleteGuest(id)
            refreshFinance()
        }
    }

    fun addEntry(
        guestId: Long,
        day: Int,
        mealType: MealType,
        paymentType: PaymentType,
        portions: Int
    ) {
        viewModelScope.launch {
            repository.addEntry(
                guestId = guestId,
                month = currentMonthFlow.value,
                day = day,
                mealType = mealType,
                paymentType = paymentType,
                portions = portions
            )
            refreshFinance()
        }
    }

    fun addEntriesForRange(
        guestId: Long,
        startDay: Int,
        endDay: Int,
        mealType: MealType,
        paymentType: PaymentType,
        portions: Int
    ) {
        viewModelScope.launch {
            repository.addEntriesForRange(
                guestId = guestId,
                month = currentMonthFlow.value,
                startDay = startDay,
                endDay = endDay,
                mealType = mealType,
                paymentType = paymentType,
                portions = portions
            )
            refreshFinance()
        }
    }

    suspend fun ratesMap() = repository.ratesMap()

    suspend fun financeSettingSnapshot() = repository.financeSetting()

    fun addExpense(name: String, paymentChannel: String, amount: Double) {
        viewModelScope.launch {
            repository.addExpense(currentMonthFlow.value, name, paymentChannel, amount)
            refreshFinance()
        }
    }

    fun updateRate(mealType: MealType, amountWithoutVat: Double) {
        viewModelScope.launch {
            repository.updateRate(mealType, amountWithoutVat)
            _rates.value = repository.ratesMap()
            refreshFinance()
        }
    }

    fun updateFinanceSetting(vatPercent: Double, taxPercent: Double) {
        viewModelScope.launch {
            repository.updateFinanceSetting(vatPercent, taxPercent)
            _financeSetting.value = repository.financeSetting()
            refreshFinance()
        }
    }

    suspend fun refreshFinance() {
        _finance.value = repository.calculateFinance(currentMonthFlow.value)
    }
}
