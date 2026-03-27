package com.cafejem.accounting.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentType { WITH_VAT, WITHOUT_VAT, CASH }
enum class MealType { BREAKFAST, LUNCH, DINNER }

@Entity
data class Guest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val roomOrOrg: String,
    val paymentType: PaymentType
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Guest::class,
            parentColumns = ["id"],
            childColumns = ["guestId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("guestId"), Index("month"), Index("day")]
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val guestId: Long,
    val month: String, // YYYY-MM
    val day: Int,
    val mealType: MealType,
    val portions: Int = 1
)

@Entity
data class MealRate(
    @PrimaryKey val mealType: MealType,
    val withoutVat: Double
)

@Entity
data class FinanceSetting(
    @PrimaryKey val id: Int = 1,
    val vatPercent: Double = 20.0,
    val taxPercent: Double = 15.0
)

@Entity(indices = [Index("month")])
data class MonthlyExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String, // YYYY-MM
    val name: String,
    val paymentChannel: String, // "cash" / "bank"
    val amount: Double
)

data class GuestSummary(
    val guestId: Long,
    val guestName: String,
    val roomOrOrg: String,
    val paymentType: PaymentType,
    val breakfastCount: Int,
    val lunchCount: Int,
    val dinnerCount: Int
) {
    fun amount(ratesWithoutVat: Map<MealType, Double>, vatMultiplier: Double): Double {
        val base = breakfastCount * (ratesWithoutVat[MealType.BREAKFAST] ?: 0.0) +
            lunchCount * (ratesWithoutVat[MealType.LUNCH] ?: 0.0) +
            dinnerCount * (ratesWithoutVat[MealType.DINNER] ?: 0.0)
        return if (paymentType == PaymentType.WITH_VAT) base * vatMultiplier else base
    }
}

data class MonthlyFinance(
    val revenueTotal: Double,
    val revenueWithVat: Double,
    val revenueWithoutVat: Double,
    val revenueCash: Double,
    val expensesTotal: Double,
    val expensesCash: Double,
    val expensesBank: Double,
    val vatTax: Double,
    val incomeTax: Double,
    val profit: Double,
    val cashLeft: Double,
    val bankLeft: Double
)
