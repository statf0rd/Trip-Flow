# Повышение покрытия кода юнит-тестами (слой логики)

Дата: 2026-06-05
Статус: дизайн (на утверждение)

## Цель

Поднять покрытие бизнес-логики юнит-тестами, не трогая продакшн-код и не
добавляя новых зависимостей. Углубить частично покрытые классы и добавить
тесты для непокрытых чистых классов слоя `data/`.

## Текущее состояние (Kover, debug)

- Общий показатель приложения: **6.0%** строк (UI/Compose, ViewModel'и и
  Bluetooth-I/O юнит-тестами не покрываются — это нормально).
- Покрытие не-UI кода: **~24.6%**.
- Ядро уже покрыто: `CategoryHeatmapCalculator` 100%, `RelayEncryption` 100%,
  `ReceiptTextParser` 94.5%, `TripNotificationScheduler` 89.5%.

## Подход

Используем уже принятый в проекте паттерн (новых зависимостей нет):

- `@RunWith(RobolectricTestRunner::class)`
- in-memory Room: `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`
- рукописные Stub-реализации API-интерфейсов (без mockk/mockito)
- `runBlocking { }` для suspend-функций, JUnit4-assertions

Продакшн-код не изменяется. Сетевые вызовы, Yandex MapKit и AI (Gemini через
`OpenAiService`) в юнит-тестах не выполняются — соответствующие ветки остаются
непокрытыми осознанно.

### Тех-долг тестов

Стабы `StubCurrencyApi`, `StubOnlineSyncApi`, `StubBackendTripApi` сейчас
приватны внутри `ExpenseRepositoryTest`. Вынести их в общий файл-фикстуру
(`app/src/test/java/com/triloo/TestApiStubs.kt`) для переиспользования в
`TripRepositoryTest`.

## Объём (вариант C — вся логика)

| Класс | Сейчас | Цель | Файл |
|---|---:|---:|---|
| `ExpenseRepository` | 48% | 85%+ | расширить `ExpenseRepositoryTest` |
| `RelayRepository` | 54% | 85%+ | расширить `RelayRepositoryTest` |
| `RelayQrCodec` | 0% | ~100% | новый `RelayQrCodecTest` |
| `TripRepository` | 0% | 85%+ | новый `TripRepositoryTest` |
| `RoutePlanningAssistant` | 45% | ~75% | расширить `RoutePlanningAssistantTest` |
| `NearestNeighborRouteOptimizer` | 63% | ~78% | расширить `NearestNeighborRouteOptimizerTest` |

### ExpenseRepository
- addExpense с иностранной валютой → конвертация по `CurrencyApi`,
  кэш курса (`saveCurrencyRate`/`getOrFetchCurrencyRate`/`getCurrencyRate`/
  `isRateStale`/`fetchLatestRate`).
- `updateExpense`; `deleteExpense` → запись в `DeletionLog`.
- `getExpenseSummary` → итоги по категориям/участникам.
- сплиты: `createExactSplits`, ветки `buildSplits` (exact/percentage).
- passthrough'и: `getExpenseById`, `getExpensesByTrip`, `observeExpensesByTrip`,
  `observeTotalExpenses`.
- `requireExpensePermission`: ветка отказа (нет прав).

### RelayRepository
- `encodePackage`/`decodePackage` round-trip (Gson).
- `encodeInvite`/`decodeInvite` round-trip.
- полный (не-delta) `buildPackage` (sinceCursor = null).
- `buildInvitePackage` + `mergeInvitePackage`.
- `applyDeletion` для всех типов: TRIP, EXPENSE, PARTICIPANT, DAY.
- `upsert*` ветка обновления (существующая сущность новее/старше).

### RelayQrCodec (новый)
- `encode`: малый payload → 1 чанк; большой при малом `chunkSize` → N чанков,
  корректный `total` и формат заголовка.
- `parse`: валидный чанк → поля; невалидный (чужой префикс, <6 частей,
  неизвестный тип, битый индекс) → null.
- `assemble`: полный набор → исходный payload (round-trip); пропуск чанка /
  неверное число / неверный первый индекс → null; пустой вход → null.

### TripRepository (новый)
- `createTrip` → вставка + авто-создание дней (`createTripDays` по диапазону);
  `getTripById`.
- `updateTrip`, `updateTripStatus`, `deleteTrip` (+ `DeletionLog`).
- места: `addPlace`, `updatePlace`, `deletePlace` (+ `DeletionLog`),
  `markPlaceVisited`, `reorderPlaces`.
- участники: `addParticipant`, `removeParticipant`,
  `updateParticipantOnlineStatus`, `updateParticipantLocation`.
- дни: `createTripDays`, `updateTripDay`, `getTripDays`.
- роли: `requireRole` и помощники — владелец разрешён, зритель отклонён.
- Стабы зависимостей: реальный `TripNotificationScheduler`, `OnlineSyncRepository`
  с stub-API (как в `ExpenseRepositoryTest`).

### RoutePlanningAssistant
- `suggestHeuristic`: ≥2 мест → план; <2 → null.
- `planRoute(preferAi = false)` → эвристика.
- `planRoute(preferAi = true)` с `OpenAiService` без ключа → fallback на
  эвристику (покрывает `buildPrompt` и null-ветку `buildAiPlan`).
- `optimizeDayOrder`: корректность порядка.
- Потолок ~75%: ветка разбора AI-JSON (`validateDayOrders`) требует валидного
  ключа/рефакторинга — вне охвата.

### NearestNeighborRouteOptimizer
- `estimateTravelTime`: известные координаты → ожидаемая длительность (haversine
  + скорость режима).
- `optimizeRoute`: порядок «ближайшего соседа» для набора точек, фиксированный
  старт.
- `buildSearchTypes`: категория → типы поиска; `toOpenRouteServiceProfile`:
  `TravelMode` → профиль.
- Вне охвата: `calculateRoute`/`fetchDirectionsRoute`/`getRecommendations`
  (OpenRouteService/Places), весь `YandexRouter` (нативный SDK).

## Вне охвата

UI/Compose, ViewModel'и, `BluetoothRelayManager` (Bluetooth-I/O), сетевые и
SDK-вызовы. Их проверка — инструментальные/ручные тесты, отдельная задача.

## Прогноз

Покрытие логики (без UI): ~25% → ~33%. Общий показатель: 6% → ~8%.
Ключевой результат для ВКР — таблица по классам: ядро уходит в 85–100%.

## Верификация

- `./gradlew testDebugUnitTest` — все тесты зелёные.
- `./gradlew koverLogDebug` — общий процент.
- `./gradlew koverHtmlReportDebug` — HTML-отчёт + проверка целевых классов
  по таблице выше.
