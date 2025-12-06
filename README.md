# 🌍 Trip Flow

**Интерактивный помощник в путешествиях** с совместным планированием маршрута, расходами и геопозициями участников.

![Android](https://img.shields.io/badge/Android-26+-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue?logo=jetpackcompose)

---

## 📱 Описание

Trip Flow объединяет функциональность нескольких тревел-приложений в одном месте:

- 📍 **Планирование маршрута** по дням с интеграцией Google Maps
- 💰 **Совместные расходы** с мультивалютностью и расчётом долгов
- 👥 **Геошаринг** — видеть друзей на карте в реальном времени
- 🎯 **Персональные рекомендации** мест на основе предпочтений

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
| **Сеть** | Retrofit + OkHttp |
| **Карты** | Google Maps SDK + Places API |
| **Асинхронность** | Kotlin Coroutines + Flow |

---

## 📦 Структура проекта

```
app/src/main/java/com/trip/flow/
├── data/
│   ├── local/           # Room Database, DAOs
│   ├── model/           # Data classes (Trip, Expense, Place...)
│   ├── remote/          # API services
│   └── repository/      # Repository implementations
├── di/                  # Hilt modules
├── ui/
│   ├── components/      # Reusable UI components
│   ├── navigation/      # Navigation graph
│   ├── theme/           # Design system (colors, typography)
│   ├── trips/           # Trip list & create screens
│   └── tripdetails/     # Trip details & tabs
├── MainActivity.kt
└── TripFlowApp.kt
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

### 🔲 Фаза 2: Core Features
- [ ] AddPlaceScreen — добавление мест через Google Places
- [ ] AddExpenseScreen — добавление расходов
- [ ] Google Maps интеграция (карта с маркерами)
- [ ] Directions API (маршруты между точками)
- [ ] Конвертация валют (API + кеширование)

### 🔲 Фаза 3: Групповые функции
- [ ] Приглашение участников (invite code)
- [ ] Синхронизация данных (Firebase / собственный backend)
- [ ] Расчёт долгов (кто кому сколько должен)
- [ ] Геошаринг (отслеживание позиций участников)

### 🔲 Фаза 4: Улучшения
- [ ] OCR для чеков (распознавание суммы)
- [ ] Рекомендации мест на основе предпочтений
- [ ] Оффлайн-режим
- [ ] Push-уведомления

---

## 🚀 Быстрый старт

### Требования
- Android Studio Hedgehog (2024.1) или новее
- JDK 17
- Android SDK 35

### Установка

1. Клонируйте репозиторий:
```bash
git clone https://github.com/statf0rd/Trip-Flow.git
cd Trip-Flow
```

2. Добавьте Google Maps API ключ в `local.properties`:
```properties
MAPS_API_KEY=YOUR_API_KEY_HERE
```

3. Откройте проект в Android Studio и запустите синхронизацию Gradle

4. Запустите приложение на эмуляторе или устройстве

---

## 📄 Лицензия

MIT License — см. [LICENSE](LICENSE)

---

## 👨‍💻 Автор

**Stanislav** — [@statf0rd](https://github.com/statf0rd)

