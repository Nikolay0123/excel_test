package com.cafejem.accounting.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AccountingRepository(private val dao: AppDao) {
    fun guestsFlow(): Flow<List<Guest>> = dao.guestsFlow()
    fun entriesFlow(month: String): Flow<List<MealEntry>> = dao.monthEntriesFlow(month)
    fun expensesFlow(month: String): Flow<List<MonthlyExpense>> = dao.monthExpensesFlow(month)

    suspend fun addGuest(name: String, roomOrOrg: String, paymentType: PaymentType) {
        dao.upsertGuest(Guest(name = name, roomOrOrg = roomOrOrg, paymentType = paymentType))
    }

    suspend fun addEntry(
        guestId: Long,
        month: String,
        day: Int,
        mealType: MealType,
        paymentType: PaymentType,
        portions: Int
    ) {
        dao.addEntry(
            MealEntry(
                guestId = guestId,
                month = month,
                day = day,
                mealType = mealType,
                paymentType = paymentType,
                portions = portions
            )
        )
    }

    suspend fun addEntriesForRange(
        guestId: Long,
        month: String,
        startDay: Int,
        endDay: Int,
        mealType: MealType,
        paymentType: PaymentType,
        portions: Int
    ) {
        for (day in startDay..endDay) {
            addEntry(
                guestId = guestId,
                month = month,
                day = day,
                mealType = mealType,
                paymentType = paymentType,
                portions = portions
            )
        }
    }

    suspend fun ensureDefaultRates() {
        if (dao.rates().isNotEmpty()) return
        dao.upsertRate(MealRate(MealType.BREAKFAST, 800.0))
        dao.upsertRate(MealRate(MealType.LUNCH, 1000.0))
        dao.upsertRate(MealRate(MealType.DINNER, 850.0))
        dao.upsertFinanceSetting(FinanceSetting())
    }

    suspend fun ratesMap(): Map<MealType, Double> = dao.rates().associate { it.mealType to it.withoutVat }
    suspend fun financeSetting(): FinanceSetting = dao.financeSetting() ?: FinanceSetting()

    suspend fun updateRate(mealType: MealType, withoutVat: Double) {
        dao.upsertRate(MealRate(mealType, withoutVat))
    }

    suspend fun updateFinanceSetting(vatPercent: Double, taxPercent: Double) {
        dao.upsertFinanceSetting(FinanceSetting(vatPercent = vatPercent, taxPercent = taxPercent))
    }

    suspend fun addExpense(month: String, name: String, paymentChannel: String, amount: Double) {
        dao.upsertExpense(
            MonthlyExpense(
                month = month,
                name = name,
                paymentChannel = paymentChannel,
                amount = amount
            )
        )
    }

    fun summaryFlow(month: String): Flow<List<GuestSummary>> =
        combine(dao.guestsFlow(), dao.monthEntriesFlow(month)) { guests, entries ->
            val grouped = entries.groupBy { it.guestId }
            guests.map { guest ->
                val list = grouped[guest.id].orEmpty()
                GuestSummary(
                    guestId = guest.id,
                    guestName = guest.name,
                    roomOrOrg = guest.roomOrOrg,
                    paymentType = guest.paymentType,
                    breakfastCount = list.filter { it.mealType == MealType.BREAKFAST }.sumOf { it.portions },
                    lunchCount = list.filter { it.mealType == MealType.LUNCH }.sumOf { it.portions },
                    dinnerCount = list.filter { it.mealType == MealType.DINNER }.sumOf { it.portions }
                )
            }
        }

    suspend fun calculateFinance(month: String): MonthlyFinance {
        val entries = dao.monthEntries(month)
        val rates = ratesMap()
        val setting = financeSetting()
        val expenseSnapshot = dao.monthExpenses(month)

        // Требование по таблице: для "доп. услуги" применяется НДС 22%.
        val vatMultiplier = 1.22
        val vatPercentForMeals = 22.0
        fun baseAmount(e: MealEntry): Double {
            val rate = rates[e.mealType] ?: 0.0
            return e.portions * rate
        }

        val withVat = entries.filter { it.paymentType == PaymentType.WITH_VAT }.sumOf { e ->
            baseAmount(e) * vatMultiplier
        }
        val withoutVat = entries.filter { it.paymentType == PaymentType.WITHOUT_VAT }.sumOf { e ->
            baseAmount(e)
        }
        val cash = entries.filter { it.paymentType == PaymentType.CASH }.sumOf { e ->
            baseAmount(e)
        }
        val revenueTotal = withVat + withoutVat + cash

        val expenseTotal = expenseSnapshot.sumOf { it.amount }
        val expenseCash = expenseSnapshot.filter { it.paymentChannel == "cash" }.sumOf { it.amount }
        val expenseBank = expenseSnapshot.filter { it.paymentChannel == "bank" }.sumOf { it.amount }

        val vatTax = if (withVat > 0) (withVat / vatMultiplier) * (vatPercentForMeals / 100.0) else 0.0
        val incomeTax = (revenueTotal - vatTax) * (setting.taxPercent / 100.0)
        val profit = revenueTotal - expenseTotal - vatTax - incomeTax
        val cashLeft = cash - expenseCash
        val bankLeft = withVat + withoutVat - expenseBank

        return MonthlyFinance(
            revenueTotal = revenueTotal,
            revenueWithVat = withVat,
            revenueWithoutVat = withoutVat,
            revenueCash = cash,
            expensesTotal = expenseTotal,
            expensesCash = expenseCash,
            expensesBank = expenseBank,
            vatTax = vatTax,
            incomeTax = incomeTax,
            profit = profit,
            cashLeft = cashLeft,
            bankLeft = bankLeft
        )
    }
}
