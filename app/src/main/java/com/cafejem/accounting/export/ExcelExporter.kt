package com.cafejem.accounting.export

import android.content.Context
import android.net.Uri
import com.cafejem.accounting.data.MealEntry
import com.cafejem.accounting.data.MealType
import com.cafejem.accounting.data.MonthlyExpense
import com.cafejem.accounting.data.MonthlyFinance
import com.cafejem.accounting.data.PaymentType
import jxl.Workbook
import jxl.write.Label
import jxl.write.Number
import jxl.write.WritableSheet
import java.time.YearMonth

class ExcelExporter {
    fun exportMonth(
        context: Context,
        targetUri: Uri,
        month: String,
        entries: List<MealEntry>,
        ratesWithoutVat: Map<MealType, Double>,
        expenses: List<MonthlyExpense>,
        finance: MonthlyFinance?
    ) {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            val wb = Workbook.createWorkbook(out)
            val sheet = wb.createSheet("${month}_питание", 0)

            val rateB = ratesWithoutVat[MealType.BREAKFAST] ?: 0.0
            val rateL = ratesWithoutVat[MealType.LUNCH] ?: 0.0
            val rateD = ratesWithoutVat[MealType.DINNER] ?: 0.0
            val vatMultiplier22 = 1.22
            val tax2Rate = 0.02

            val yearMonth = YearMonth.parse(month)
            val days = yearMonth.lengthOfMonth()
            val entriesByDay = entries.groupBy { it.day }

            val headers = listOf(
                "День",
                "Завтраки (кол-во)", "Завтраки (сумма)",
                "Обеды (кол-во)", "Обеды (сумма)",
                "Ужины (кол-во)", "Ужины (сумма)",
                "Налог 2% (цена номера)"
            )
            headers.forEachIndexed { col, value -> sheet.addCell(Label(col, 0, value)) }

            // Totals for the entire month
            var monthBreakfastCount = 0
            var monthLunchCount = 0
            var monthDinnerCount = 0
            var monthBreakfastSum = 0.0
            var monthLunchSum = 0.0
            var monthDinnerSum = 0.0

            var roomAmount = 0.0       // без НДС (paymentType=WITHOUT_VAT)
            var additionalAmount = 0.0 // с НДС 22% (paymentType=WITH_VAT)
            var cashAmount = 0.0       // за наличные

            fun baseAmount(e: MealEntry): Double {
                val rate = when (e.mealType) {
                    MealType.BREAKFAST -> rateB
                    MealType.LUNCH -> rateL
                    MealType.DINNER -> rateD
                }
                return e.portions * rate
            }

            fun chargedAmount(e: MealEntry): Double {
                val base = baseAmount(e)
                return if (e.paymentType == PaymentType.WITH_VAT) base * vatMultiplier22 else base
            }

            for (day in 1..days) {
                val dayEntries = entriesByDay[day].orEmpty()

                val breakfastEntries = dayEntries.filter { it.mealType == MealType.BREAKFAST }
                val lunchEntries = dayEntries.filter { it.mealType == MealType.LUNCH }
                val dinnerEntries = dayEntries.filter { it.mealType == MealType.DINNER }

                val dayBreakfastCount = breakfastEntries.sumOf { it.portions }
                val dayLunchCount = lunchEntries.sumOf { it.portions }
                val dayDinnerCount = dinnerEntries.sumOf { it.portions }

                val dayBreakfastSum = breakfastEntries.sumOf { chargedAmount(it) }
                val dayLunchSum = lunchEntries.sumOf { chargedAmount(it) }
                val dayDinnerSum = dinnerEntries.sumOf { chargedAmount(it) }

                val dayRoomBase = dayEntries
                    .filter { it.paymentType == PaymentType.WITHOUT_VAT }
                    .sumOf { baseAmount(it) }
                val dayTax2 = dayRoomBase * tax2Rate

                val row = day
                sheet.addCell(Label(0, row, day.toString()))
                sheet.addCell(Number(1, row, dayBreakfastCount.toDouble()))
                sheet.addCell(Number(2, row, dayBreakfastSum))
                sheet.addCell(Number(3, row, dayLunchCount.toDouble()))
                sheet.addCell(Number(4, row, dayLunchSum))
                sheet.addCell(Number(5, row, dayDinnerCount.toDouble()))
                sheet.addCell(Number(6, row, dayDinnerSum))
                sheet.addCell(Number(7, row, dayTax2))

                monthBreakfastCount += dayBreakfastCount
                monthLunchCount += dayLunchCount
                monthDinnerCount += dayDinnerCount
                monthBreakfastSum += dayBreakfastSum
                monthLunchSum += dayLunchSum
                monthDinnerSum += dayDinnerSum

                roomAmount += dayRoomBase
                additionalAmount += dayEntries
                    .filter { it.paymentType == PaymentType.WITH_VAT }
                    .sumOf { baseAmount(it) } * vatMultiplier22
                cashAmount += dayEntries
                    .filter { it.paymentType == PaymentType.CASH }
                    .sumOf { baseAmount(it) }
            }

            val totalRow = days + 1
            sheet.addCell(Label(0, totalRow, "ИТОГО за месяц"))
            sheet.addCell(Number(1, totalRow, monthBreakfastCount.toDouble()))
            sheet.addCell(Number(2, totalRow, monthBreakfastSum))
            sheet.addCell(Number(3, totalRow, monthLunchCount.toDouble()))
            sheet.addCell(Number(4, totalRow, monthLunchSum))
            sheet.addCell(Number(5, totalRow, monthDinnerCount.toDouble()))
            sheet.addCell(Number(6, totalRow, monthDinnerSum))
            sheet.addCell(Number(7, totalRow, roomAmount * tax2Rate))

            val sectionStart = totalRow + 2
            sheet.addCell(Label(0, sectionStart + 0, "Сумма за питание в цене номера (без НДС)"))
            sheet.addCell(Number(1, sectionStart + 0, roomAmount))
            sheet.addCell(Label(0, sectionStart + 1, "Сумма за питание как доп. услуга (НДС 22%)"))
            sheet.addCell(Number(1, sectionStart + 1, additionalAmount))
            sheet.addCell(Label(0, sectionStart + 2, "Сумма за питание за наличные"))
            sheet.addCell(Number(1, sectionStart + 2, cashAmount))
            sheet.addCell(Label(0, sectionStart + 3, "Налог 2% (с питания в цене номера)"))
            sheet.addCell(Number(1, sectionStart + 3, roomAmount * tax2Rate))
            sheet.addCell(Label(0, sectionStart + 4, "Общая сумма за питание за месяц"))
            sheet.addCell(Number(1, sectionStart + 4, roomAmount + additionalAmount + cashAmount))

            val financeSheet = wb.createSheet("${month}_финансы", 1)
            writeFinance(
                sheet = financeSheet,
                rateB = rateB,
                rateL = rateL,
                rateD = rateD,
                expenses = expenses,
                finance = finance
            )

            wb.write()
            wb.close()
        }
    }

    private fun writeFinance(
        sheet: WritableSheet,
        rateB: Double,
        rateL: Double,
        rateD: Double,
        expenses: List<MonthlyExpense>,
        finance: MonthlyFinance?
    ) {
        val rows = mutableListOf<Pair<String, String>>(
            "Ставка завтрак (без НДС)" to rateB.toString(),
            "Ставка обед (без НДС)" to rateL.toString(),
            "Ставка ужин (без НДС)" to rateD.toString()
        )
        finance?.let {
            rows += listOf(
                "Выручка всего" to it.revenueTotal.toString(),
                "Выручка с НДС" to it.revenueWithVat.toString(),
                "Выручка без НДС" to it.revenueWithoutVat.toString(),
                "Выручка наличными" to it.revenueCash.toString(),
                "Расходы всего" to it.expensesTotal.toString(),
                "НДС налог" to it.vatTax.toString(),
                "Налог 15%" to it.incomeTax.toString(),
                "Прибыль" to it.profit.toString(),
                "Остаток наличных" to it.cashLeft.toString(),
                "Остаток на счете" to it.bankLeft.toString()
            )
        }
        rows.forEachIndexed { idx, item ->
            sheet.addCell(Label(0, idx, item.first))
            sheet.addCell(Label(1, idx, item.second))
        }
        val start = rows.size + 2
        sheet.addCell(Label(0, start, "Издержки"))
        sheet.addCell(Label(1, start, "Канал"))
        sheet.addCell(Label(2, start, "Сумма"))
        expenses.forEachIndexed { idx, exp ->
            val r = start + 1 + idx
            sheet.addCell(Label(0, r, exp.name))
            sheet.addCell(Label(1, r, if (exp.paymentChannel == "cash") "Наличные" else "Безнал"))
            sheet.addCell(Number(2, r, exp.amount))
        }
    }

    private fun paymentLabel(paymentType: PaymentType): String = when (paymentType) {
        PaymentType.WITH_VAT -> "С НДС"
        PaymentType.WITHOUT_VAT -> "Без НДС"
        PaymentType.CASH -> "Наличные"
    }
}
