package com.triloo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.triloo.ui.components.IconCompassLucide
import com.triloo.ui.components.IconGearLucide
import com.triloo.ui.components.IconPlusLucide
import com.triloo.ui.components.IconUsersLucide
import com.triloo.ui.components.IconWalletLucide
import com.triloo.ui.components.LiquidGlassNavBar
import com.triloo.ui.components.LiquidGlassTab
import com.triloo.ui.navigation.Screen
import com.triloo.ui.trips.CreateTripModalSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triloo.data.settings.AppLanguage
import com.triloo.data.settings.ThemeMode
import com.triloo.ui.navigation.TrilooNavHost
import com.triloo.ui.settings.AppSettingsViewModel
import com.triloo.ui.theme.TrilooTheme
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Подключаем splash screen до вызова super.onCreate, как требует AndroidX API.
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Полноэкранный режим: прячем оба системных бара (статус сверху и
        // навигационный снизу — back/home/recents). Поведение sticky: свайп
        // от края временно проявляет бары на пару секунд. Воздух сверху для
        // контента отдельно докладываем через корневой Box ниже.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val settingsViewModel: AppSettingsViewModel = hiltViewModel()
            val uiState = settingsViewModel.uiState.collectAsStateWithLifecycle().value
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val localeTag = when (uiState.language) {
                AppLanguage.RU -> "ru"
                AppLanguage.EN -> "en"
            }

            LaunchedEffect(uiState.language) {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(localeTag)
                )
            }

            TrilooTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Так как системные бары спрятаны, у системы нет верхнего
                    // inset'а — контент липнет к верхнему краю. Добавляем
                    // воздух в корне + сохраняем поправку на display-cutout
                    // (челка/динамический остров — у системы они учитываются
                    // отдельным insets, даже когда бары скрыты).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Top)
                            )
                            .padding(top = 6.dp)
                    ) {
                        // Контейнер навигации + плавающий нав-бар. Создаём
                        // NavController наверху, чтобы и NavHost, и навбар
                        // смотрели на одно и то же состояние маршрута.
                        val navController = rememberNavController()
                        // Глобальная шторка «Что хотите сделать?» — одна
                        // на всё приложение, триггерится центральной FAB-
                        // кнопкой нав-бара (`+`) и CTA-карточкой «Куда
                        // дальше?» внутри списка поездок.
                        var showCreateSheet by rememberSaveable { mutableStateOf(false) }

                        // .imePadding() читает анимированный WindowInsets.ime и
                        // плавно сжимает NavHost снизу синхронно с системной
                        // IME-анимацией. Без него edge-to-edge окно при
                        // adjustResize ресайзится «прыжком» — контент дёргается
                        // в момент показа/скрытия клавиатуры.
                        TrilooNavHost(
                            navController = navController,
                            modifier = Modifier.imePadding(),
                            onShowCreateTripSheet = { showCreateSheet = true }
                        )

                        val currentEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = currentEntry?.destination?.route

                        // Прячем нав-бар, когда поднята клавиатура: иначе он
                        // либо перекрывается ею (если без imePadding'а), либо
                        // прилипает прямо к её краю (с imePadding'ом) — оба
                        // варианта выглядят неряшливо, и активные поля ввода
                        // всё равно живут внутри контента, а не на табах.
                        val density = LocalDensity.current
                        val isImeVisible = WindowInsets.ime.getBottom(density) > 0

                        // Список табов один-к-одному по дизайну App Shell:
                        // Поездки / Группы / Бюджет / Настройки. По центру
                        // ряда — отдельная FAB-кнопка `+` (создание поездки).
                        val tabs = remember {
                            listOf(
                                LiquidGlassTab(Screen.TripList.route, "Поездки", IconCompassLucide),
                                LiquidGlassTab(Screen.GroupTrips.route, "Группы", IconUsersLucide),
                                LiquidGlassTab(Screen.Budget.route, "Бюджет", IconWalletLucide),
                                LiquidGlassTab(Screen.Settings.route, "Настр.", IconGearLucide)
                            )
                        }
                        // Бар показываем только на корнях табов: на детальных
                        // и оверлейных экранах он бы перекрывал контент. Плюс
                        // прячем при поднятой клавиатуре.
                        val activeIndex = tabs.indexOfFirst { it.id == currentRoute }
                        if (activeIndex >= 0 && !isImeVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 18.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                LiquidGlassNavBar(
                                    tabs = tabs,
                                    activeIndex = activeIndex,
                                    hazeState = null,
                                    centerActionIcon = IconPlusLucide,
                                    centerActionContentDescription = "Создать путешествие",
                                    onCenterAction = { showCreateSheet = true },
                                    onTabSelected = { idx ->
                                        val target = tabs[idx].id
                                        if (target != currentRoute) {
                                            navController.navigate(target) {
                                                // Не плодим стек одинаковых корней при повторном тапе.
                                                launchSingleTop = true
                                                // Поднимаемся к стартовой вершине, сохраняя её состояние,
                                                // чтобы переключение между табами не теряло скролл/фильтры.
                                                popUpTo(Screen.TripList.route) {
                                                    saveState = true
                                                }
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (showCreateSheet) {
                            CreateTripModalSheet(
                                onDismiss = { showCreateSheet = false },
                                onCreatePersonalTrip = {
                                    navController.navigate(
                                        Screen.CreateTrip.createRoute(isGroupTrip = false)
                                    )
                                },
                                onCreateGroupTrip = {
                                    navController.navigate(
                                        Screen.CreateTrip.createRoute(isGroupTrip = true)
                                    )
                                },
                                onJoinGroupTrip = {
                                    navController.navigate(Screen.GroupTrips.route)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.APP_MAPKIT_VIEW_ENABLED && BuildConfig.APP_MAPKIT_API_KEY.isNotBlank()) {
            MapKitFactory.getInstance().onStart()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Пересборщик: если система временно показала бары (после
        // уведомления, диалога или возврата из фона), при возврате фокуса
        // прячем их обратно.
        if (hasFocus) {
            WindowCompat.getInsetsController(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        if (BuildConfig.APP_MAPKIT_VIEW_ENABLED && BuildConfig.APP_MAPKIT_API_KEY.isNotBlank()) {
            MapKitFactory.getInstance().onStop()
        }
        super.onStop()
    }
}
