# 🌍 Triloo

**Интерактивный помощник в путешествиях** с совместным планированием маршрута, расходами и геопозициями участников.

![Android](https://img.shields.io/badge/Android-26+-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue?logo=jetpackcompose)

---

## 📱 Описание

Triloo объединяет функциональность нескольких тревел-приложений в одном месте:

- 📍 **Планирование маршрута** по дням с интеграцией Google Maps
- 💰 **Совместные расходы** с мультивалютностью и расчётом долгов
- 👥 **Геошаринг** — видеть друзей на карте в реальном времени
- 🔗 **Групповые поездки** с кодами приглашений и QR‑приглашениями
- 📡 **Triloo Relay** — офлайн‑синхронизация поездок через Bluetooth
- 🎯 **Персональные рекомендации** мест на основе предпочтений
- 🌓 **Темная тема** с настройкой режима
- 🔐 **Опциональная авторизация** для сохранения профиля

---

## 🏗️ Архитектура

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

## 🛠️ Технологии

| Категория | Технология |
|-----------|------------|
| **UI** | Jetpack Compose + Material 3 |
| **Архитектура** | MVVM + Clean Architecture |
| **DI** | Hilt |
| **Навигация** | Navigation Compose |
| **БД** | Room |
| **Хранение настроек** | DataStore |
| **Сеть** | Retrofit + OkHttp |
| **Backend** | Firebase (Auth, Firestore) |
| **Карты** | Google Maps SDK + Places API |
| **QR** | ZXing |
| **Асинхронность** | Kotlin Coroutines + Flow |

---

## 📦 Структура проекта

```
app/src/main/java/com/triloo/
├── data/
│   ├── local/           # Room Database, DAOs
│   ├── model/           # Data classes (Trip, Expense, Place...)
│   ├── relay/           # Triloo Relay (offline sync)
│   ├── settings/        # App settings (theme, prefs)
│   ├── user/            # Local user profile
│   ├── remote/          # API clients (Places, Directions, Currency)
│   └── repository/      # Repository implementations
├── di/                  # Hilt modules
├── ui/
│   ├── auth/            # Optional auth flow
│   ├── components/      # Reusable UI components
│   ├── invite/          # QR invitations
│   ├── navigation/      # Navigation graph
│   ├── qr/              # QR generator/scanner
│   ├── relay/           # Triloo Relay UI
│   ├── theme/           # Design system (colors, typography)
│   ├── trips/           # Trip list & create screens
│   └── tripdetails/     # Trip details & tabs
├── MainActivity.kt
└── TrilooApp.kt
```

---

## 🎨 Дизайн-система

### Цветовая палитра

| Цвет | HEX | Назначение |
|------|-----|------------|
| 🔴 Coral Primary | `#FF6B5B` | Главные действия |
| 🟢 Teal Secondary | `#2DD4BF` | Вторичные действия, успех |
| 🟡 Golden Accent | `#FBBF24` | Валюта, акценты |
| ⚫ Slate | `#0F172A` - `#F8FAFC` | Текст, фоны |

### Типографика

- **Заголовки**: Outfit (Bold/SemiBold)
- **Текст**: DM Sans (Regular/Medium)

---

## 📋 MVP Чек-лист

> Подробный roadmap с техническим долгом, частично готовыми фичами и backlog'ом — в `obsi/00-Index/Roadmap.md` (Obsidian vault проекта).

### ✅ Фаза 1: Основа (Готово)
- [x] Настройка проекта (Gradle, зависимости)
- [x] Дизайн-система (цвета, типографика, компоненты)
- [x] Модели данных (Trip, TripDay, Place, Expense, Participant)
- [x] Room Database + DAOs
- [x] Репозитории (TripRepository, ExpenseRepository)
- [x] Hilt DI
- [x] Навигация
- [x] TripListScreen — список поездок
- [x] CreateTripScreen — создание поездки
- [x] TripDetailsScreen — детали с вкладками

### ✅ Фаза 2: Core Features (Готово)
- [x] AddPlaceScreen — добавление мест с автокомплитом (Geoapify + Yandex Geosuggest + Google Places)
- [x] AddExpenseScreen — добавление расходов
- [x] Карты (Yandex MapKit основной + Google Maps fallback, через `:feature-map` модуль)
- [x] Directions / OpenRouteService API подключён, nearest-neighbor оптимизатор маршрута
- [x] Конвертация валют (open.er-api.com + кэш в Room)

### 🟡 Фаза 3: Групповые функции (Частично)
- [x] Приглашение участников (invite code + QR через ZXing)
- [x] Triloo Relay — офлайн‑синхронизация через Bluetooth + QR-чанки с AES-256
- [x] Расчёт долгов (`ExpenseRepository.calculateBalances`, 5 split types в модели)
- [x] Геошаринг участников через Foreground Service + FusedLocationProviderClient
- [ ] Онлайн‑синхронизация — backend stubs (Node.js Express в `/server`), endpoints `/sync` не дописаны
- [ ] Полноценные сплиты в UI — пока только PAYER_ONLY в `AddExpenseScreen`
- [ ] HTTPS вместо `usesCleartextTraffic="true"` для backend
- [ ] Conflict resolution per-field (сейчас Last-Write-Wins на всю запись)

### 🟡 Фаза 4: Улучшения (Частично)
- [x] OCR для чеков (Google ML Kit Text Recognition, on-device)
- [x] Оффлайн‑режим (Triloo Relay)
- [x] Crashlytics + Privacy Policy + Notification channels
- [ ] Рекомендации мест на основе предпочтений (Gemini/OpenAI scaffolding в `data/ai/OpenAiService.kt`)
- [ ] Push-уведомления через FCM
- [ ] Heatmap категорий на карте (расчёт в `CategoryHeatmapCalculator`, рендер не сделан)
- [ ] ETA между точками в UI таймлайна
- [ ] Email-верификация и password reset для auth

### 💡 Backlog / идеи
- iOS-портабельность через Kotlin Multiplatform (потребует переезд репозиториев в shared)
- CRDT-based replication вместо Last-Write-Wins
- Кеш карт и маркеров для офлайн-режима
- Расширенная аналитика расходов: графики, экспорт CSV/JSON
- Hard-delete API для GDPR (право на забвение)
- TTL для `DeletionLog` (сейчас растёт без ограничений)

---

## 🚀 Быстрый старт

### Требования
- Android Studio Hedgehog (2024.1) или новее
- JDK 17
- Android SDK 36

### Установка

1. Клонируйте репозиторий:
```bash
git clone https://github.com/statf0rd/Trip-Flow.git
cd Trip-Flow
```

2. Добавьте Google Maps API ключ и Google Web Client ID в `local.properties`:
```properties
MAPS_API_KEY=YOUR_API_KEY_HERE
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

3. Скачайте `google-services.json` из Firebase Console и положите в `app/`:
```text
app/google-services.json
```
   Также включите Email/Google провайдеры и добавьте SHA-1/SHA-256 для Google Sign-In.

4. Откройте проект в Android Studio и запустите синхронизацию Gradle

5. Запустите приложение на эмуляторе или устройстве

---

## 📄 Лицензия

MIT License — см. [LICENSE](LICENSE)

---

## 🔒 Политика конфиденциальности

См. [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

---

## 👨‍💻 Автор

**Stanislav** — [@statf0rd](https://github.com/statf0rd)


