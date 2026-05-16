# Triloo

Мобильное приложение для совместного планирования путешествий: маршрут, расходы, геолокация участников и офлайн-синхронизация между устройствами.

Дипломный проект по теме **«Интегрированные средства помощи в путешествиях»**.

![Android](https://img.shields.io/badge/Android-26+-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue?logo=jetpackcompose)

---

## Описание

Triloo объединяет в одном клиенте функции, обычно разнесённые по нескольким приложениям:

- **Планирование маршрута** по дням с автодополнением мест (Geoapify, Yandex Geosuggest)
- **Оптимизация маршрута** алгоритмом ближайшего соседа поверх OpenRouteService и Yandex-маршрутизаторов
- **Учёт расходов** с мультивалютностью и автоматическим расчётом долгов между участниками
- **Геошаринг** — отображение положения участников группы на карте в реальном времени
- **Групповые поездки** с приглашением по коду
- **Triloo Relay** — офлайн-синхронизация состояния поездки между устройствами по Bluetooth Classic с шифрованием AES-256
- **Онлайн-синхронизация** через HTTPS-backend с ролевым контролем доступа
- **Распознавание чеков** (on-device OCR через ML Kit)
- **Рекомендации жилья** на основе геоданных и эвристик
- **Тёмная тема** и опциональная авторизация

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                        PRESENTATION (UI)                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │TripList │ │TripEdit │ │DayPlan  │ │MapView  │ │Expenses │   │
│  │Screen   │ │Screen   │ │Screen   │ │Screen   │ │Screen   │   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘   │
│       └───────────┴───────────┴───────────┴───────────┘         │
│                           ViewModels                             │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                    DOMAIN (Repositories)                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                 │
│  │TripRepo    │  │ExpenseRepo │  │CurrencyRepo│                 │
│  └────────────┘  └────────────┘  └────────────┘                 │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                        DATA LAYER                                │
│  ┌───────────┐   ┌───────────┐   ┌───────────┐                  │
│  │  Room DB  │   │ Retrofit  │   │ DataStore │                  │
│  └───────────┘   └───────────┘   └───────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Технологии

| Категория | Технология |
|-----------|------------|
| UI | Jetpack Compose, Material 3 |
| Архитектура | MVVM, Clean Architecture |
| DI | Hilt |
| Навигация | Navigation Compose |
| Локальная БД | Room |
| Хранение настроек | DataStore |
| Сеть | Retrofit, OkHttp |
| Backend (онлайн-синк) | Node.js, Express, better-sqlite3 |
| Аутентификация | Firebase Auth |
| Картография | Yandex MapKit |
| Геокодинг и автодополнение | Geoapify, Yandex Geosuggest |
| Маршрутизация | OpenRouteService, Yandex Transport |
| Распознавание текста | Google ML Kit (on-device) |
| Беспроводная синхронизация | Bluetooth Classic RFCOMM, AES-256 |
| Асинхронность | Kotlin Coroutines, Flow |

---

## Структура проекта

```
app/src/main/java/com/triloo/
├── data/
│   ├── accommodation/   # Сервис рекомендаций жилья (Geoapify + эвристики)
│   ├── ai/              # Scaffolding для AI-сервисов (Gemini, OpenAI)
│   ├── local/           # Room Database, DAOs
│   ├── location/        # Геошаринг через Foreground Service
│   ├── model/           # Доменные модели (Trip, Expense, Place, ...)
│   ├── ocr/             # OCR чеков через ML Kit
│   ├── relay/           # Bluetooth Relay (офлайн-синхронизация)
│   ├── remote/          # Сетевые клиенты (Geoapify, OpenRouteService, ...)
│   ├── repository/      # Реализации репозиториев
│   ├── route/           # Алгоритмы оптимизации маршрута
│   ├── settings/        # Настройки приложения
│   └── user/            # Локальный профиль пользователя
├── di/                  # Hilt-модули
├── ui/
│   ├── auth/            # Авторизация
│   ├── budget/          # Глобальный экран бюджета
│   ├── components/      # Переиспользуемые UI-компоненты
│   ├── grouptrips/      # Групповые поездки
│   ├── invite/          # Приглашения по коду
│   ├── navigation/      # Граф навигации
│   ├── relay/           # UI Bluetooth-синхронизации
│   ├── settings/        # Настройки и политика конфиденциальности
│   ├── theme/           # Дизайн-система (цвета, типографика)
│   ├── tripdetails/     # Детали поездки и вкладки
│   └── trips/           # Список и создание поездок
├── MainActivity.kt
└── TrilooApp.kt

feature-map/             # Модуль Yandex MapKit (TripYandexMapView, MapPickerView)
server/                  # Node.js backend для онлайн-синхронизации
```

---

## Ключевые алгоритмы

| Алгоритм | Файл | Описание |
|----------|------|----------|
| Оптимизация маршрута (nearest-neighbor) | `data/route/RouteOptimizer.kt` | Жадная перестановка точек маршрута с подтягиванием реальных дистанций через Yandex Transport или OpenRouteService |
| Расчёт взаимных долгов | `data/repository/ExpenseRepository.kt` (`calculateBalances`) | Нормализация расходов к базовой валюте через `CurrencyRepository` и сведение балансов участников |
| Bluetooth-синхронизация снэпшотов | `data/relay/BluetoothRelayManager.kt` | RFCOMM-соединение, шифрование AES-256, передача полного снэпшота при первом коннекте и только изменений на последующих |
| Распознавание чеков | `data/ocr/...` | On-device OCR через Google ML Kit с извлечением суммы и валюты |
| Ранжирование жилья | `data/accommodation/AccommodationRecommendationService.kt` | Эвристики (расстояние от точки, бюджет, рейтинг, звёздность) поверх Geoapify Places |

---

## Дизайн-система

### Цветовая палитра

| Цвет | HEX | Назначение |
|------|-----|------------|
| Coral Primary | `#FF6B5B` | Главные действия |
| Teal Secondary | `#2DD4BF` | Вторичные действия, индикация успеха |
| Golden Accent | `#FBBF24` | Денежные акценты |
| Slate | `#0F172A` – `#F8FAFC` | Текст, фоны |

### Типографика

- Заголовки: **Outfit** (Bold, SemiBold)
- Основной текст: **DM Sans** (Regular, Medium)

---

## Состояние реализации

### Фаза 1. Базовая инфраструктура

- [x] Конфигурация Gradle и зависимостей
- [x] Дизайн-система (цвета, типографика, компоненты)
- [x] Доменные модели (Trip, TripDay, Place, Expense, Participant)
- [x] Room Database и DAO
- [x] Репозитории (TripRepository, ExpenseRepository)
- [x] Hilt DI
- [x] Граф навигации
- [x] Экран списка поездок (TripListScreen)
- [x] Экран создания поездки (CreateTripScreen)
- [x] Экран деталей поездки с вкладками (TripDetailsScreen)

### Фаза 2. Базовая функциональность

- [x] Добавление мест с автодополнением (Geoapify + Yandex Geosuggest)
- [x] Добавление расходов
- [x] Карта на Yandex MapKit (модуль `:feature-map`)
- [x] Маршрутизация через OpenRouteService и Yandex Transport
- [x] Оптимизация маршрута алгоритмом ближайшего соседа
- [x] Мультивалютная конвертация (open.er-api.com, кэш в Room)

### Фаза 3. Групповые функции

- [x] Приглашение участников по инвайт-коду
- [x] Расчёт долгов (`ExpenseRepository.calculateBalances`, поддержка пяти типов сплита в модели)
- [x] Геошаринг через Foreground Service и FusedLocationProviderClient
- [x] Triloo Relay — офлайн-синхронизация снэпшота поездки между устройствами по Bluetooth Classic RFCOMM с шифрованием AES-256
- [x] Онлайн-синхронизация снэпшотов через HTTPS-backend (Node.js Express, `/sync/push` и `/sync/pull` с ролевым контролем OWNER / ADMIN / MEMBER)
- [x] HTTPS-only backend (network security config, default URL `https://triloo.85.192.61.86.nip.io/`)
- [ ] Полноценные сплиты в UI (модель поддерживает EQUAL / EXACT / PERCENTAGE / SHARES, в `AddExpenseScreen` пока только PAYER_ONLY)
- [ ] Per-field conflict resolution (текущая стратегия — Last-Write-Wins на полный снэпшот)

### Фаза 4. Дополнительные возможности

- [x] OCR чеков (Google ML Kit Text Recognition, on-device)
- [x] Офлайн-режим (Triloo Relay)
- [x] Рекомендации жилья (Geoapify Places + эвристики)
- [x] Crashlytics, политика конфиденциальности, каналы уведомлений
- [ ] AI-рекомендации мест на основе предпочтений (scaffolding в `data/ai/OpenAiService.kt`, интеграция не завершена)
- [ ] Push-уведомления через FCM
- [ ] Heatmap категорий на карте (расчёт в `CategoryHeatmapCalculator`, рендер не подключён)
- [ ] ETA между точками в таймлайне
- [ ] Email-верификация и восстановление пароля

### Перспективы развития

- iOS-портабельность через Kotlin Multiplatform (с выносом репозиториев в shared-модуль)
- CRDT-репликация вместо Last-Write-Wins
- Офлайн-кэш карт и маркеров
- Расширенная аналитика расходов: графики, экспорт CSV/JSON
- Hard-delete API для GDPR
- TTL для `DeletionLog`

---

## Тестирование

В проекте подключены unit-тесты на JVM:

- `app/src/test/java/com/triloo/RelayRepositoryTest.kt` — проверка слияния входящих relay-пакетов, идемпотентность повторных применений
- `app/src/test/java/com/triloo/NearestNeighborRouteOptimizerTest.kt` — построение маршрута и подбор рекомендаций по точкам

Запуск:

```bash
./gradlew :app:testDebugUnitTest
```

---

## Быстрый старт

### Требования

- Android Studio Hedgehog (2024.1) или новее
- JDK 17
- Android SDK 36
- Yandex MapKit API-ключ (получить на developer.tech.yandex.ru)
- Geoapify API-ключ (myprojects.geoapify.com)
- OpenRouteService API-ключ (openrouteservice.org)
- (Опционально) Yandex Geosuggest API-ключ, Gemini API-ключи

### Установка

1. Клонировать репозиторий:

```bash
git clone https://github.com/statf0rd/Trip-Flow.git
cd Trip-Flow
```

2. Создать файл `local.properties` в корне проекта со следующими ключами:

```properties
sdk.dir=/path/to/Android/sdk

MAPKIT_API_KEY=YOUR_YANDEX_MAPKIT_API_KEY
MAPKIT_MAP_ENABLED=true
GEOAPIFY_API_KEY=YOUR_GEOAPIFY_API_KEY
GEOSUGGEST_API_KEY=YOUR_YANDEX_GEOSUGGEST_API_KEY
OPENROUTESERVICE_API_KEY=YOUR_OPENROUTESERVICE_API_KEY

# Опционально:
GEMINI_API_KEYS=KEY_1,KEY_2
TRILOO_BACKEND_URL=https://triloo.85.192.61.86.nip.io/
```

Без `MAPKIT_MAP_ENABLED=true` приложение показывает заглушку «Карта временно недоступна».

3. Положить `google-services.json` из Firebase Console в `app/`:

```text
app/google-services.json
```

В Firebase включить провайдеры Email и Google, добавить SHA-1 / SHA-256 устройства для Google Sign-In.

4. Открыть проект в Android Studio, дождаться синхронизации Gradle.

5. Запустить приложение на эмуляторе или устройстве (минимальная версия Android 8.0, API 26).

---

## Лицензия

MIT License — см. [LICENSE](LICENSE).

---

## Политика конфиденциальности

См. [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

---

## Автор

Stanislav — [@statf0rd](https://github.com/statf0rd)
