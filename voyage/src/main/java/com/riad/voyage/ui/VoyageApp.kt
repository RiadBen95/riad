package com.riad.voyage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riad.voyage.AppViewModel
import com.riad.voyage.CitySection
import com.riad.voyage.Tab
import com.riad.voyage.data.LitePayment
import com.riad.voyage.data.NotablePlaces

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
    val pendingBooking by vm.pendingBooking.collectAsStateWithLifecycle()
    val citySection by vm.citySection.collectAsStateWithLifecycle()
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
                        selected = tab == t || (t == Tab.CITY && (tab == Tab.ACTIVITIES || tab == Tab.BLOG)),
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
                    onOpenBudget = { vm.selectTab(Tab.BUDGET) },
                    trips = trips,
                    onOpenTrip = vm::openTrip,
                    onOpenAlerts = { vm.selectTab(Tab.ALERTS) },
                    onOpenSettings = configure,
                    onRecent = vm::applyRecent,
                    onClearRecents = vm::clearRecents,
                    onDestination = vm::pickDestination,
                    wiki = vm::wikiSummary,
                    weather = vm::weatherFor,
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

                Tab.CITY, Tab.ACTIVITIES, Tab.BLOG -> {
                    BackHandler {
                        when {
                            citySection == CitySection.ARTICLES && (blog.article != null || blog.articleLoading) -> vm.closeArticle()
                            citySection == CitySection.ARTICLES && blog.city != null -> vm.clearBlogCity()
                            else -> vm.selectTab(Tab.HOME)
                        }
                    }
                    CityScreen(
                        section = citySection,
                        onSection = vm::setCitySection,
                        places = {
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
                                weather = vm::weatherFor,
                                isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                                onFavorite = { p -> vm.toggleFavorite(p, activityCity.ifBlank { places.city }) },
                                onReadArticle = vm::readPlaceArticle,
                            )
                        },
                        articles = {
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
                                    NotablePlaces.supports(activityCategory),
                                onSaveArticle = vm::saveArticle,
                            )
                        },
                        info = {
                            val here = vm.currentCity()
                            PracticalInfo(
                                city = here?.first, country = here?.second.orEmpty(),
                                info = here?.let { vm.practicalFor(it.first, it.second) },
                                myCurrency = settings.currency, rates = vm::rates,
                            )
                        },
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

                Tab.HOTELS -> {
                    val detail = hotels.detail
                    BackHandler { if (detail != null) vm.closeHotelDetail() else vm.selectTab(Tab.HOME) }
                    if (detail != null) {
                        HotelDetailScreen(
                            state = hotels,
                            d = detail,
                            sandbox = LitePayment.isSandbox(settings.liteApiKey),
                            onBack = vm::closeHotelDetail,
                            onBook = vm::startHotelBooking,
                            onConfirm = vm::confirmHotelBooking,
                            onCancelBooking = vm::cancelHotelBooking,
                            onPaymentDone = vm::hotelPaymentDone,
                            paidPending = pendingBooking != null,
                            onRetryPending = vm::retryPendingBooking,
                            onPaymentCancel = vm::hotelPaymentCancelled,
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
                            onBookInApp = vm::openHotelDetail,
                            pendingHotel = pendingBooking?.hotelName,
                            onRetryPending = vm::retryPendingBooking,
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
                        weather = vm::weatherFor,
                        onReadArticle2 = vm::readPlaceArticle,
                        onBudget = vm::openBudgetForTrip,
                        onHotels = { tr ->
                            vm.openHotels(tr.city, null, tr.start, tr.end)
                        },
                        alertCount = alerts.size,
                        actions = TripActions(
                            moveStep = vm::moveStep, deleteStep = vm::deleteStep, editStep = vm::editStep, addStep = vm::addStep,
                            addDay = vm::addDay, packing = vm::packingFor, togglePacked = vm::togglePacked,
                            addPackItem = vm::addPackItem, removePackItem = vm::removePackItem, practical = vm::openPractical,
                        ),
                    )
                }

                Tab.BUDGET -> {
                    BackHandler { if (budget != null) vm.closeBudget() else vm.selectTab(Tab.HOME) }
                    val b = budget
                    if (b == null) {
                        BudgetPicker(
                            trips = trips,
                            experienceCity = experience.result?.city,
                            onTrip = vm::openBudgetForTrip,
                            onExperience = vm::openBudgetForExperience,
                            onCreate = { vm.selectTab(Tab.EXPERIENCE) },
                        )
                    } else {
                        val budgetTrip = trips.firstOrNull { it.id == b.tripId }
                        BudgetScreen(
                            onChoose = vm::chooseForBudget,
                            state = b,
                            currency = settings.currency,
                            onBack = vm::closeBudget,
                            onInput = vm::updateBudgetInput,
                            onOverride = vm::setBudgetOverride,
                            onTarget = vm::setBudgetTarget,
                            expenses = budgetTrip?.expenses.orEmpty(),
                            localCurrency = vm.practicalFor(b.input.city, b.input.country)?.currency,
                            onAddExpense = budgetTrip?.let { tr -> { label, amount, cur, item -> vm.addExpense(tr.id, label, amount, cur, item) } },
                            onRemoveExpense = { id -> budgetTrip?.let { vm.removeExpense(it.id, id) } },
                            rates = vm::rates,
                        )
                    }
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
    Tab.CITY -> AppIcons.City
    Tab.FLIGHTS -> AppIcons.Flight
    Tab.ACTIVITIES -> AppIcons.Ticket
    Tab.EXPERIENCE -> AppIcons.Sparkle
    Tab.BLOG -> AppIcons.Book
    Tab.TRIPS -> AppIcons.Luggage
    Tab.HOTELS -> AppIcons.Hotel
    Tab.ALERTS -> AppIcons.Bell
    Tab.BUDGET -> AppIcons.Wallet
    Tab.SETTINGS -> AppIcons.Settings
}
