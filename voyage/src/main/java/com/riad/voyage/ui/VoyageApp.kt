package com.riad.voyage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riad.voyage.AppViewModel
import com.riad.voyage.Tab

@Composable
fun VoyageApp(vm: AppViewModel, onFollowRequested: () -> Unit) {
    val context = LocalContext.current
    val tab by vm.tab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val activityCity by vm.activityCity.collectAsStateWithLifecycle()
    val activityCategory by vm.activityCategory.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val activityPlace by vm.activityPlace.collectAsStateWithLifecycle()
    val experience by vm.experience.collectAsStateWithLifecycle()
    val blog by vm.blog.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val openTrip by vm.openTrip.collectAsStateWithLifecycle()
    val tripArticle by vm.tripArticle.collectAsStateWithLifecycle()
    val hotels by vm.hotels.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val budgetReturn by vm.budgetReturn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val configure = { vm.selectTab(Tab.SETTINGS) }
    // Vérifie la clé enregistrée au démarrage, pour afficher son état dans Réglages.
    LaunchedEffect(Unit) { vm.testKey() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { androidx.compose.foundation.layout.Column {
            if (budgetReturn && tab != Tab.BUDGET && budget != null) {
                androidx.compose.material3.Button(
                    onClick = vm::backToBudget,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) { Text(com.riad.voyage.t("💰 Retour au budget")) }
            }
            NavigationBar {
                Tab.entries.filter { it.inBottomBar }.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { vm.selectTab(t) },
                        icon = { Icon(tabIcon(t), contentDescription = com.riad.voyage.t(t.label)) },
                        label = { Text(com.riad.voyage.t(t.label), maxLines = 1, fontSize = 10.sp, softWrap = false) },
                    )
                }
            }
        } },
    ) { padding ->
        // L'accueil dessine son en-tête sous la barre d'état ; les autres écrans restent dessous.
        val contentPadding = if (tab == Tab.HOME) PaddingValues(bottom = padding.calculateBottomPadding()) else padding
        Box(
            Modifier
                .padding(contentPadding)
                .fillMaxSize(),
        ) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    form = form,
                    recents = recents,
                    alerts = alerts,
                    demo = settings.isDemo,
                    onOpenFlights = { vm.selectTab(Tab.FLIGHTS) },
                    onOpenActivities = { vm.selectTab(Tab.ACTIVITIES) },
                    onOpenExperience = { vm.selectTab(Tab.EXPERIENCE) },
                    onOpenBlog = { vm.selectTab(Tab.BLOG) },
                    onOpenHotels = { vm.openHotels(null, null, null, null) },
                    trips = trips,
                    onOpenTrip = vm::openTrip,
                    onOpenAlerts = { vm.selectTab(Tab.ALERTS) },
                    onOpenSettings = configure,
                    onRecent = vm::applyRecent,
                    onDestination = vm::pickDestination,
                    wiki = vm::wikiSummary,
                )

                Tab.FLIGHTS -> if (results.visible) {
                    BackHandler { vm.closeResults() }
                    FlightResultsScreen(
                        state = results,
                        onBack = vm::closeResults,
                        onRetry = vm::search,
                        onSort = vm::setSort,
                        onPickDay = vm::pickCalendarDay,
                        onBook = { f -> openUrl(context, vm.bookingUrl(f)) },
                        onActivities = vm::activitiesAtDestination,
                        onPlanStay = { vm.planStayFromFlight(null) },
                        onHotels = {
                            val q = results.query
                            vm.openHotels(q?.destination?.name, q?.destination, q?.departure,
                                if (q?.roundTrip == true && q.flexibleMonth != true) q.returnDate else null, q?.adults)
                        },
                        onSaveFlight = vm::saveFlight,
                        onFollow = {
                            onFollowRequested()
                            vm.followCurrentSearch()
                        },
                        onOpenSite = { vm.siteSearchUrl()?.let { openUrl(context, it) } },
                        onConfigure = configure,
                    )
                } else {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    FlightSearchScreen(
                        form = form,
                        demo = settings.isDemo,
                        onUpdate = vm::updateForm,
                        onSwap = vm::swapPlaces,
                        onSearch = vm::search,
                        suggest = vm::suggestPlaces,
                        onConfigure = configure,
                        recents = recents,
                        onRecent = vm::applyRecent,
                    )
                }

                Tab.ACTIVITIES -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    ActivitiesScreen(
                        city = activityCity,
                        category = activityCategory,
                        places = places,
                        onCity = vm::setActivityCity,
                        selectedPlace = activityPlace,
                        onPlace = vm::setActivityPlace,
                        suggestCities = vm::suggestCities,
                        onCategory = vm::setActivityCategory,
                        onSearch = { vm.searchActivities(force = true) },
                        wiki = vm::wikiSummary,
                        isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                        onFavorite = { p -> vm.toggleFavorite(p, activityCity.ifBlank { places.city }) },
                        onReadArticle = vm::readPlaceArticle,
                    )
                }

                Tab.EXPERIENCE -> {
                    BackHandler {
                        when {
                            experience.result != null || experience.loading -> vm.editExperience()
                            experience.step > 0 -> vm.quizStep(-1)
                            else -> vm.selectTab(Tab.HOME)
                        }
                    }
                    ExperienceScreen(
                        state = experience,
                        onAnswers = vm::updateAnswers,
                        onStep = vm::quizStep,
                        onGenerate = vm::generateExperience,
                        onRegenerate = vm::regenerateExperience,
                        onEdit = vm::editExperience,
                        suggestCities = vm::suggestCities,
                        onSave = vm::saveExperience,
                        alternatives = vm::stepAlternatives,
                        onHotels = {
                            val a = experience.answers
                            vm.openHotels(a.city, a.place, a.startDate, a.startDate.plusDays(a.duration.days.toLong()))
                        },
                        onReplace = vm::replaceExperienceStep,
                        onBudget = vm::openBudgetForExperience,
                        wiki = vm::wikiSummary,
                        isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                        onFavorite = { p -> vm.toggleFavorite(p, experience.result?.city ?: experience.answers.city) },
                        onReadArticle = vm::readPlaceArticle,
                    )
                }

                Tab.BLOG -> {
                    BackHandler {
                        when {
                            blog.article != null || blog.articleLoading -> vm.closeArticle()
                            blog.city != null -> vm.clearBlogCity()
                            else -> vm.selectTab(Tab.HOME)
                        }
                    }
                    BlogScreen(
                        state = blog,
                        context = vm.blogContext(),
                        onCity = vm::openBlogCity,
                        onPlace = vm::openBlogPlace,
                        onClearCity = vm::clearBlogCity,
                        onFilter = vm::setBlogFilter,
                        onArticle = vm::openArticle,
                        onCloseArticle = vm::closeArticle,
                        suggestCities = vm::suggestCities,
                        searchArticles = vm::searchArticles,
                        wiki = vm::wikiSummary,
                        hasPersonalFilter = experience.answers.interests.isNotEmpty() ||
                            com.riad.voyage.data.NotablePlaces.supports(activityCategory),
                        onSaveArticle = vm::saveArticle,
                    )
                }

                Tab.HOTELS -> {
                    val detail = hotels.detail
                    BackHandler { if (detail != null) vm.closeHotelDetail() else vm.selectTab(Tab.HOME) }
                    if (detail != null) {
                        HotelDetailScreen(
                            state = hotels,
                            d = detail,
                            sandbox = settings.liteApiKey.startsWith("sandbox"),
                            onBack = vm::closeHotelDetail,
                            onBook = vm::startHotelBooking,
                            onConfirm = vm::confirmHotelBooking,
                            onCancelBooking = vm::cancelHotelBooking,
                        )
                    } else {
                        HotelsScreen(
                            state = hotels,
                            hasKey = settings.liteApiKey.isNotBlank(),
                            onUpdate = vm::updateHotels,
                            onSearch = vm::searchHotels,
                            onSave = vm::saveHotel,
                            isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                            suggestCities = vm::suggestCities,
                            onConfigure = configure,
                            onOpen = vm::openHotelDetail,
                        )
                    }
                }

                Tab.TRIPS -> {
                    BackHandler {
                        when {
                            tripArticle != null -> vm.readSavedArticle(null)
                            openTrip != null -> vm.openTrip(null)
                            else -> vm.selectTab(Tab.HOME)
                        }
                    }
                    TripsScreen(
                        trips = trips,
                        openId = openTrip,
                        article = tripArticle,
                        onOpen = vm::openTrip,
                        onDelete = vm::deleteTrip,
                        onRemove = vm::removeFromTrip,
                        onReadArticle = vm::readSavedArticle,
                        onAlerts = { vm.selectTab(Tab.ALERTS) },
                        wiki = vm::wikiSummary,
                        onReadArticle2 = vm::readPlaceArticle,
                        onBudget = vm::openBudgetForTrip,
                        onHotels = { tr ->
                            vm.openHotels(tr.city, null, tr.startDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                                tr.endDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() })
                        },
                        alertCount = alerts.size,
                    )
                }

                Tab.BUDGET -> {
                    BackHandler { vm.closeBudget() }
                    budget?.let { b ->
                        BudgetScreen(
                            onChoose = vm::chooseForBudget,
                            state = b,
                            currency = settings.currency,
                            onBack = vm::closeBudget,
                            onInput = vm::updateBudgetInput,
                            onOverride = vm::setBudgetOverride,
                            onTarget = vm::setBudgetTarget,
                        )
                    } ?: LaunchedEffect(Unit) { vm.selectTab(Tab.HOME) }
                }

                Tab.ALERTS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    AlertsScreen(alerts, settings.isDemo, checking, vm::checkAlertsNow, vm::removeAlert, configure)
                }

                Tab.SETTINGS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    SettingsScreen(settings, keyStatus, vm::saveSettings, vm.quotas, vm::setQuotaLimit, vm::setLanguage)
                }
            }
        }
    }
}

private fun tabIcon(t: Tab) = when (t) {
    Tab.HOME -> AppIcons.Home
    Tab.FLIGHTS -> AppIcons.Flight
    Tab.ACTIVITIES -> AppIcons.Ticket
    Tab.EXPERIENCE -> AppIcons.Sparkle
    Tab.BLOG -> AppIcons.Book
    Tab.TRIPS -> AppIcons.Luggage
    Tab.HOTELS -> AppIcons.Hotel
    Tab.ALERTS -> AppIcons.Bell
    Tab.BUDGET -> AppIcons.Luggage
    Tab.SETTINGS -> AppIcons.Settings
}
