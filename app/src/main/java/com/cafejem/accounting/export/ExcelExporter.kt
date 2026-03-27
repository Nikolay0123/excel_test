package com.cafejem.accounting.export

import android.content.Context
import android.net.Uri
import com.cafejem.accounting.data.MonthlyExpense
import com.cafejem.accounting.data.MonthlyFinance
import com.cafejem.accounting.data.GuestSummary
import com.cafejem.accounting.data.MealType
import com.cafejem.accounting.data.PaymentType
import org.apache.poi.xssf.usermodel.XSSFWorkbook

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
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(month)

        val rateB = ratesWithoutVat[MealType.BREAKFAST] ?: 0.0
        val rateL = ratesWithoutVat[MealType.LUNCH] ?: 0.0
        val rateD = ratesWithoutVat[MealType.DINNER] ?: 0.0
        val vatMultiplier = 1.2

        val header = sheet.createRow(0)
        listOf(
            "Гость/Организация", "Комната/Орг", "Тип оплаты",
            "Завтраки", "Обеды", "Ужины", "Сумма, руб"
        ).forEachIndexed { i, value -> header.createCell(i).setCellValue(value) }

        var rowIdx = 1
        summary.forEach { s ->
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(s.guestName)
            row.createCell(1).setCellValue(s.roomOrOrg)
            row.createCell(2).setCellValue(
                when (s.paymentType) {
                    PaymentType.WITH_VAT -> "Да"
                    PaymentType.WITHOUT_VAT -> "Нет"
                    PaymentType.CASH -> "Налич"
                }
            )
            row.createCell(3).setCellValue(s.breakfastCount.toDouble())
            row.createCell(4).setCellValue(s.lunchCount.toDouble())
            row.createCell(5).setCellValue(s.dinnerCount.toDouble())

            val base = s.breakfastCount * rateB + s.lunchCount * rateL + s.dinnerCount * rateD
            val total = if (s.paymentType == PaymentType.WITH_VAT) base * vatMultiplier else base
            row.createCell(6).setCellValue(total)
        }

        val totalRow = sheet.createRow(rowIdx + 1)
        totalRow.createCell(0).setCellValue("ИТОГО")
        totalRow.createCell(3).setCellFormula("SUM(D2:D${rowIdx})")
        totalRow.createCell(4).setCellFormula("SUM(E2:E${rowIdx})")
        totalRow.createCell(5).setCellFormula("SUM(F2:F${rowIdx})")
        totalRow.createCell(6).setCellFormula("SUM(G2:G${rowIdx})")

        for (i in 0..6) sheet.autoSizeColumn(i)

        val financeSheet = wb.createSheet("$month-финансы")
        val financeRows = mutableListOf<Pair<String, String>>(
            "Ставка завтрак (без НДС)" to rateB.toString(),
            "Ставка обед (без НДС)" to rateL.toString(),
            "Ставка ужин (без НДС)" to rateD.toString()
        )
        finance?.let {
            financeRows += listOf(
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

        financeRows.forEachIndexed { index, pair ->
            val row = financeSheet.createRow(index)
            row.createCell(0).setCellValue(pair.first)
            row.createCell(1).setCellValue(pair.second)
        }

        val expenseStart = financeRows.size + 2
        val expenseHeader = financeSheet.createRow(expenseStart)
        expenseHeader.createCell(0).setCellValue("Издержки")
        expenseHeader.createCell(1).setCellValue("Канал")
        expenseHeader.createCell(2).setCellValue("Сумма")
        expenses.forEachIndexed { idx, exp ->
            val row = financeSheet.createRow(expenseStart + 1 + idx)
            row.createCell(0).setCellValue(exp.name)
            row.createCell(1).setCellValue(exp.paymentChannel)
            row.createCell(2).setCellValue(exp.amount)
        }
        for (i in 0..2) financeSheet.autoSizeColumn(i)

        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            wb.write(out)
        }
        wb.close()
    }
}
