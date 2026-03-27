# Cafe Jem Accounting (Android)

Android-приложение для учета кухни/питания, сделанное на основе Excel-файла.

## Что уже есть

- учет гостей/организаций;
- выбор типа оплаты (`WITH_VAT`, `WITHOUT_VAT`, `CASH`);
- ввод факта питания по кнопкам/формам (`завтрак`, `обед`, `ужин`, день, порции);
- агрегированные итоги;
- экспорт в `.xlsx`.

## Стек

- Kotlin
- Jetpack Compose
- Room
- Apache POI (экспорт Excel)

## Структура

- `app/src/main/java/com/cafejem/accounting/MainActivity.kt` — UI;
- `app/src/main/java/com/cafejem/accounting/AppViewModel.kt` — состояние и команды;
- `app/src/main/java/com/cafejem/accounting/data/` — сущности, DAO, БД, репозиторий;
- `app/src/main/java/com/cafejem/accounting/export/ExcelExporter.kt` — экспорт.

## Важно

Файл `ANALYSIS_AND_PRODUCT_SPEC_RU.md` содержит разбор исходной таблицы и roadmap до полного соответствия 1:1.
