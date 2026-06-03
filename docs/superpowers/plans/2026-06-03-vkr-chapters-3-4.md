# План реализации: главы 3, 4 и Заключение ВКР Triloo

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline) — задачи по чекбоксам (`- [ ]`). Писать прозу глав нужно в едином голосе с гл.1–2, поэтому исполнение — inline, не субагентами.

**Goal:** Дописать `ВКР.md` главами 3 (реализация), 4 (экспериментальная проверка с реальными измерениями и скриншотами) и Заключением; цифры гл.4 — из реального прогона тестов.

**Architecture:** Сначала измерительные JVM-тесты дают реальные числа, затем по ним и по коду пишется проза (гибрид: листинги + формулы + TikZ), каждая глава проходит /humanizer, вшивается в `ВКР.md`, PDF пересобирается pandoc+xelatex.

**Tech Stack:** Markdown + LaTeX (pandoc/xelatex/TikZ), Kotlin unit-тесты (JUnit4, Robolectric, runBlocking), gradle.

Опирается на spec: [docs/superpowers/specs/2026-06-03-vkr-chapters-3-4-design.md](docs/superpowers/specs/2026-06-03-vkr-chapters-3-4-design.md).

Команда сборки PDF (везде ниже — «собрать PDF»):
```bash
export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"
pandoc ВКР.md -o ВКР.pdf --pdf-engine=xelatex -V documentclass=extarticle -V papersize=a4 \
  -V fontsize=14pt -V geometry:"left=30mm,right=10mm,top=20mm,bottom=20mm" \
  -V mainfont="Times New Roman" -V linestretch=1.5 -V lang=ru -V indent=true
```

---

## Фаза 0. Правки честности в гл.1 + ветка

- [ ] **Шаг 0.1 — F5.** В `ВКР.md` (строка ~560) заменить текст требования F5: убрать «с фиксацией стартовой и конечной точки» → «с фиксацией стартовой точки»; «поверх реальных дистанций из маршрутных API» → «по геодезическим расстояниям (формула гаверсинусов); реальные дорожные дистанции из маршрутных API используются для построения и оценки итогового маршрута».
- [ ] **Шаг 0.2 — критерий маршрута.** В §1.4 (строка ~608) «поверх реальных дистанций из маршрутного API» → «по геодезическим расстояниям (формула гаверсинусов)».
- [ ] **Шаг 0.3 — F8.** Строка ~563: «...EXACT, PERCENTAGE, SHARES; расчёт...» → «...EXACT (режимы PERCENTAGE и SHARES предусмотрены моделью данных и отнесены к развитию); расчёт...».
- [ ] **Шаг 0.4 — сборка+коммит.** Собрать PDF (0 ошибок), `git add ВКР.md && git commit -m "docs(vkr): выравнивание F5/F8 под фактическую реализацию" && git push origin main`.

## Фаза 1. Измерительные тесты (реальные числа до прозы)

### Задача 1.1 — Тест безопасности канала (§4.5)
**Files:** Create `app/src/test/java/com/triloo/RelayEncryptionTest.kt`

- [ ] **Шаг 1** — написать тест (полный код):
```kotlin
package com.triloo

import com.triloo.data.relay.RelayEncryption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

class RelayEncryptionTest {

    @Test
    fun roundTripDecryptsToOriginal() {
        val key = RelayEncryption.deriveKey("trip-secret-123")
        val plain = "RelayPackage — поездка в Сеул".toByteArray(Charsets.UTF_8)
        val dec = RelayEncryption.decrypt(RelayEncryption.encrypt(plain, key), key)
        assertArrayEquals(plain, dec)
    }

    @Test
    fun wrongSecretFailsAuthentication() {
        val enc = RelayEncryption.encrypt("secret".toByteArray(), RelayEncryption.deriveKey("trip-A"))
        try {
            RelayEncryption.decrypt(enc, RelayEncryption.deriveKey("trip-B"))
            fail("ожидался отказ проверки тега GCM при неверном секрете")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e is BadPaddingException)
        }
    }

    @Test
    fun tamperedByteFailsTagCheck() {
        val key = RelayEncryption.deriveKey("trip-X")
        val enc = RelayEncryption.encrypt("payload".toByteArray(), key).copyOf()
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0x01).toByte()
        try {
            RelayEncryption.decrypt(enc, key)
            fail("ожидался отказ: искажён 1 байт")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e is BadPaddingException)
        }
    }

    @Test
    fun ivIsRandomizedPerEncryption() {
        val key = RelayEncryption.deriveKey("trip-Y")
        val plain = "same".toByteArray()
        val e1 = RelayEncryption.encrypt(plain, key)
        val e2 = RelayEncryption.encrypt(plain, key)
        assertFalse(e1.copyOfRange(0, 12).contentEquals(e2.copyOfRange(0, 12)))
        assertFalse(e1.contentEquals(e2))
    }
}
```
- [ ] **Шаг 2** — запустить: `./gradlew testDebugUnitTest --tests "com.triloo.RelayEncryptionTest"`. Ожидание: 4 PASS. Зафиксировать результат для §4.5.

### Задача 1.2 — Замер качества маршрута (§4.3)
**Files:** Create `app/src/test/java/com/triloo/RouteQualityMeasurementTest.kt`

- [ ] **Шаг 1** — написать тест (полный код): NN из реального `NearestNeighborRouteOptimizer.optimizeRoute` (только гаверсинус, сеть не вызывается); оптимум — Хелд–Карп (открытый маршрут, свободные концы); сиды фиксированы.
```kotlin
package com.triloo

import com.triloo.data.model.Place
import com.triloo.data.model.PlaceCategory
import com.triloo.data.model.TravelMode
import com.triloo.data.places.NearbyPlacesProvider
import com.triloo.data.places.PlaceSuggestion
import com.triloo.data.remote.OpenRouteServiceApi
import com.triloo.data.remote.OpenRouteServiceDirectionsRequest
import com.triloo.data.remote.OpenRouteServiceDirectionsResponse
import com.triloo.data.route.MapRouteProvider
import com.triloo.data.route.NearestNeighborRouteOptimizer
import com.triloo.data.route.YandexRouteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RouteQualityMeasurementTest {

    private fun haversine(a: Place, b: Place): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun pathLength(order: List<Place>): Double =
        order.zipWithNext().sumOf { (x, y) -> haversine(x, y) }

    private fun optimalOpenPath(points: List<Place>): Double {
        val n = points.size
        if (n <= 1) return 0.0
        val d = Array(n) { i -> DoubleArray(n) { j -> haversine(points[i], points[j]) } }
        val full = 1 shl n
        val dp = Array(full) { DoubleArray(n) { Double.POSITIVE_INFINITY } }
        for (i in 0 until n) dp[1 shl i][i] = 0.0
        for (mask in 0 until full) for (i in 0 until n) {
            val cur = dp[mask][i]
            if (cur == Double.POSITIVE_INFINITY || mask and (1 shl i) == 0) continue
            for (j in 0 until n) {
                if (mask and (1 shl j) != 0) continue
                val nm = mask or (1 shl j)
                val c = cur + d[i][j]
                if (c < dp[nm][j]) dp[nm][j] = c
            }
        }
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until n) best = min(best, dp[full - 1][i])
        return best
    }

    private fun optimizer() = NearestNeighborRouteOptimizer(
        openRouteServiceApi = object : OpenRouteServiceApi {
            override suspend fun getDirections(
                profile: String, apiKey: String, request: OpenRouteServiceDirectionsRequest
            ): OpenRouteServiceDirectionsResponse = OpenRouteServiceDirectionsResponse(routes = emptyList())
        },
        nearbyPlacesProvider = object : NearbyPlacesProvider {
            override suspend fun getNearbyPlaces(
                latitude: Double, longitude: Double, radius: Int, type: String?
            ): List<PlaceSuggestion> = emptyList()
        },
        mapRouteProvider = object : MapRouteProvider {
            override suspend fun route(places: List<Place>, mode: TravelMode): YandexRouteResult? = null
        }
    )

    private fun place(lat: Double, lng: Double) = Place(
        tripId = "t", tripDayId = "d", name = "p", latitude = lat, longitude = lng,
        category = PlaceCategory.ATTRACTION
    )

    @Test
    fun nearestNeighborQualityAcrossSizes() = runBlocking {
        val opt = optimizer()
        val sizes = listOf(5, 7, 10, 12, 15)
        val setsPerSize = 20
        val latMin = 37.45; val latMax = 37.62; val lngMin = 126.85; val lngMax = 127.12
        println("== Качество NN (среднее по $setsPerSize наборам, Сеул) ==")
        println("N | исходный,м | NN,м | оптимум,м | выигрыш,% | откл.от опт.,%")
        for (n in sizes) {
            val rnd = Random(42L + n)
            var sOrig = 0.0; var sNn = 0.0; var sOpt = 0.0; var sImpr = 0.0; var sDev = 0.0
            repeat(setsPerSize) {
                val pts = (0 until n).map {
                    place(latMin + rnd.nextDouble() * (latMax - latMin),
                          lngMin + rnd.nextDouble() * (lngMax - lngMin))
                }
                val orig = pathLength(pts)
                val nn = pathLength(opt.optimizeRoute(pts, startLocation = null).optimizedPlaces)
                val best = optimalOpenPath(pts)
                sOrig += orig; sNn += nn; sOpt += best
                sImpr += if (orig > 0) (orig - nn) / orig * 100 else 0.0
                sDev += if (best > 0) (nn - best) / best * 100 else 0.0
            }
            val k = setsPerSize.toDouble()
            println(String.format(Locale.US, "%2d | %10.0f | %8.0f | %9.0f | %8.1f | %8.1f",
                n, sOrig / k, sNn / k, sOpt / k, sImpr / k, sDev / k))
            assertTrue("NN не короче оптимума", sNn / k >= sOpt / k - 1.0)
        }
    }
}
```
- [ ] **Шаг 2** — запустить: `./gradlew testDebugUnitTest --tests "com.triloo.RouteQualityMeasurementTest" -i 2>&1 | grep -A20 "Качество NN"`. Скопировать таблицу чисел → данные для Табл. 7. Если `OpenRouteServiceDirectionsResponse(routes = emptyList())` не компилируется из-за обязательных полей — посмотреть data class и добавить недостающие (это не влияет на результат, т.к. в `optimizeRoute` API не вызывается).

### Задача 1.3 — Идемпотентность слияния Relay (§4.4)
**Files:** Modify `app/src/test/java/com/triloo/RelayRepositoryTest.kt` (добавить тест в существующий класс с тем же harness)

- [ ] **Шаг 1** — прочитать существующий `mergePackageInsertsTripDataAndAppliesDeletion` (строки 58–128), чтобы повторить точную сборку `RelayPackage`. Добавить метод:
```kotlin
@Test
fun mergePackageIsIdempotentOnReapply() = runBlocking {
    // Локально: поездка + участник-владелец (как в существующем тесте).
    // Построить RelayPackage с одним новым местом (updatedAt = 4000), как в строках 73–120.
    // Слить дважды одним и тем же пакетом, проверить отсутствие дублей.
    val pkg = /* собрать RelayPackage точь-в-точь как в mergePackageInsertsTripDataAndAppliesDeletion */ TODO_REPLACE
    repository.mergePackage(pkg)
    val afterFirst = database.placeDao().getPlacesByTripOnce(pkg.trip.id).size
    repository.mergePackage(pkg)
    val afterSecond = database.placeDao().getPlacesByTripOnce(pkg.trip.id).size
    assertEquals(afterFirst, afterSecond)
}
```
  Примечание: точное имя DAO-метода выборки мест уточнить при чтении `PlaceDao` (например `getPlacesByTrip`/`getPlacesForTrip`); собрать `RelayPackage` копированием конструкции из существующего теста (НЕ оставлять `TODO_REPLACE`).
- [ ] **Шаг 2** — запустить весь класс: `./gradlew testDebugUnitTest --tests "com.triloo.RelayRepositoryTest"`. Ожидание: все PASS (3 существующих + новый). Зафиксировать для Табл. 8.

### Задача 1.4 — Воркед-пример долгов на 3 участника (§3.2/§4.2)
**Files:** Modify `app/src/test/java/com/triloo/ExpenseRepositoryTest.kt`

- [ ] **Шаг 1** — прочитать `setUp`/harness существующего `ExpenseRepositoryTest` (как конструируется `ExpenseRepository` со стабами). Добавить тест: трип EUR, участники A/B/C; A платит 120 (EQUAL на троих → каждому по 40), B платит 60 (EQUAL → по 20). Нетто: A −80…, посчитать и сверить минимальный набор переводов из `calculateBalances`. Полный код собрать по образцу существующего теста; ассертить количество переводов и суммы.
- [ ] **Шаг 2** — запустить: `./gradlew testDebugUnitTest --tests "com.triloo.ExpenseRepositoryTest"`. PASS. Зафиксировать числа примера.

- [ ] **Шаг 1.5 — коммит фазы 1.** `git add app/src/test && git commit -m "test(vkr): измерительные тесты для гл.4 (маршрут, AES-GCM, идемпотентность, долги)" && git push origin main`.

## Фаза 2. Глава 3 (после получения чисел)

- [ ] **Шаг 2.1 — черновик гл.3** по spec §4: §3.1 (Листинг 1 `nearestNeighbor`, формула гаверсинуса, Рис. 8), §3.2 (Табл. 5 режимы, Листинг 2 `simplifyDebts`, формула баланса, Рис. 9, пример из Задачи 1.4), §3.3 (Листинг 3 `encrypt/decrypt`, ключ=SHA-256(`tripId`), правило LWW/удаления Листинг 4), §3.4 (переиспользование доменного слоя, push/pull, ролевой контроль, честно о зрелости), §3.5 выводы. Цитаты: [4,5,13] §3.1, [6] §3.3, [7,8] §3.3, [14] §3.3/3.4, [9,17] §3.1. Черновик в `docs/superpowers/drafts/ch3.md`.
- [ ] **Шаг 2.2 — /humanizer** по тексту гл.3: применить skill, убрать признаки ИИ-письма, выправить.
- [ ] **Шаг 2.3 — вшить** гл.3 в `ВКР.md` между §2.5 (после строки с выводами гл.2) и разделом источников. TikZ Рис. 8/9 — добавлять и проверять сборку инкрементально.
- [ ] **Шаг 2.4 — собрать PDF**, убедиться: 0 ошибок LaTeX, схемы/листинги/таблица отображаются.
- [ ] **Шаг 2.5 — коммит.** `git add ВКР.md && git commit -m "feat(vkr): глава 3 — реализация ключевых подсистем" && git push origin main`.

## Фаза 3. Глава 4

- [ ] **Шаг 3.1 — скриншоты (gate).** `adb devices`; если пусто — попытаться поднять эмулятор (`emulator -list-avds`, запуск), собрать+поставить `./gradlew installDebug`, пройти сценарий «Сеул» и снять 6–10 экранов `adb exec-out screencap -p > docs/superpowers/assets/NN.png`. Если недостижимо в разумных пределах — деградировать §4.2 до таблицы покрытия без скриншотов и сообщить пользователю. Файлы — в `app/`-доступный путь для pandoc (например `assets/`).
- [ ] **Шаг 3.2 — черновик гл.4** по spec §5 с реальными числами из Фазы 1: §4.1 методика; §4.2 Табл. 6 (F1–F15) + скриншоты (Рис. N…); §4.3 Табл. 7 (из Задачи 1.2); §4.4 Табл. 8 (из Задач 1.3 + RelayRepositoryTest); §4.5 (из Задачи 1.1); §4.6 офлайн-наблюдения; §4.7 выводы.
- [ ] **Шаг 3.3 — /humanizer** по тексту гл.4.
- [ ] **Шаг 3.4 — вшить** гл.4 после гл.3; **перенумеровать** все Рис./Табл. и ссылки «рис. N/табл. N» по всему документу.
- [ ] **Шаг 3.5 — собрать PDF**, проверить.
- [ ] **Шаг 3.6 — коммит.** `git add -A && git commit -m "feat(vkr): глава 4 — экспериментальная проверка (реальные измерения, скриншоты)" && git push origin main`.

## Фаза 4. Заключение

- [ ] **Шаг 4.1 — черновик** по spec §6 (итоги задач 1–5, новизна, перспективы: CRDT [14], доп. транспорты, усиление ключа, PERCENTAGE/SHARES, закалка backend).
- [ ] **Шаг 4.2 — /humanizer**.
- [ ] **Шаг 4.3 — вшить** после гл.4, перед разделом источников. Собрать PDF.
- [ ] **Шаг 4.4 — коммит.** `git add ВКР.md && git commit -m "feat(vkr): заключение" && git push origin main`.

## Фаза 5. Финальная сверка

- [ ] **Шаг 5.1** — аудит сквозной нумерации Рис./Табл. и всех ссылок; финальная сборка PDF; число страниц; 0 ошибок LaTeX (кроме прежнего предупреждения про ₽).
- [ ] **Шаг 5.2** — обновить память `project-vkr-pdf-build` (новые рисунки/таблицы, итоговая нумерация).
- [ ] **Шаг 5.3 — финальный коммит** при необходимости + отчёт пользователю.

## Self-review (покрытие spec)
- Критерии §1.4 → §4.2 (F1–F15), §4.3 (маршрут), §4.4 (слияние), §4.5 (безопасность), §4.6 (офлайн). ✓
- Подсистемы гл.3 §3.1–3.4 ↔ spec §4. ✓
- Правки честности F5/F8 → Фаза 0. ✓
- Реальные числа → Фаза 1 до прозы. ✓
- /humanizer на каждой главе → Шаги 2.2, 3.3, 4.2. ✓
- Риск скриншотов → Шаг 3.1 с фолбэком. ✓
