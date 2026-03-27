package com.cafejem.accounting.export

import android.content.Context
import android.net.Uri
import com.cafejem.accounting.data.MonthlyExpense
import com.cafejem.accounting.data.MonthlyFinance
import com.cafejem.accounting.data.GuestSummary
import com.cafejem.accounting.data.MealType
import com.cafejem.accounting.data.PaymentType
import jxl.Workbook
import jxl.write.Label
import jxl.write.Number
import jxl.write.WritableSheet

class ExcelExporter {
    fun exportMonth(
        context: Context,
        targetUri: Uri,
        month: String,
        summary: List<GuestSummary>,
        ratesWithoutVat: Map<MealType, Double>,
        expenses: List<MonthlyExpense>,
        finance: MonthlyFinance?
    ) {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            val wb = Workbook.createWorkbook(out)
            val sheet = wb.createSheet(month, 0)

            val rateB = ratesWithoutVat[MealType.BREAKFAST] ?: 0.0
            val rateL = ratesWithoutVat[MealType.LUNCH] ?: 0.0
            val rateD = ratesWithoutVat[MealType.DINNER] ?: 0.0
            val vatMultiplier = 1.2

            val headers = listOf(
                "Гость/Организация", "Комната/Орг", "Тип оплаты",
                "Завтраки", "Обеды", "Ужины", "Сумма, руб"
            )
            headers.forEachIndexed { col, value -> sheet.addCell(Label(col, 0, value)) }

            var rowIdx = 1
            summary.forEach { s ->
                sheet.addCell(Label(0, rowIdx, s.guestName))
                sheet.addCell(Label(1, rowIdx, s.roomOrOrg))
                sheet.addCell(Label(2, rowIdx, paymentLabel(s.paymentType)))
                sheet.addCell(Number(3, rowIdx, s.breakfastCount.toDouble()))
                sheet.addCell(Number(4, rowIdx, s.lunchCount.toDouble()))
                sheet.addCell(Number(5, rowIdx, s.dinnerCount.toDouble()))
                val base = s.breakfastCount * rateB + s.lunchCount * rateL + s.dinnerCount * rateD
                val total = if (s.paymentType == PaymentType.WITH_VAT) base * vatMultiplier else base
                sheet.addCell(Number(6, rowIdx, total))
                rowIdx++
            }

            sheet.addCell(Label(0, rowIdx + 1, "ИТОГО"))
            sheet.addCell(Label(3, rowIdx + 1, summary.sumOf { it.breakfastCount }.toString()))
            sheet.addCell(Label(4, rowIdx + 1, summary.sumOf { it.lunchCount }.toString()))
            sheet.addCell(Label(5, rowIdx + 1, summary.sumOf { it.dinnerCount }.toString()))
            sheet.addCell(Number(6, rowIdx + 1, summary.sumOf {
                val base = it.breakfastCount * rateB + it.lunchCount * rateL + it.dinnerCount * rateD
                if (it.paymentType == PaymentType.WITH_VAT) base * vatMultiplier else base
            }))

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

    private fun paymentLabel(paymentType: PaymentType): String {
        return when (paymentType) {
            PaymentType.WITH_VAT -> "С НДС"
            PaymentType.WITHOUT_VAT -> "Без НДС"
            PaymentType.CASH -> "Наличные"
        }
    }
}
