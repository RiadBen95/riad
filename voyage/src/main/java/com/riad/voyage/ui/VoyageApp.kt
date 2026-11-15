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
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.data.NotablePlaces

@Composable
fun VoyageApp(vm: AppViewModel, onFollowRequested: () -> Unit) {
    val context = LocalContext.current
    val tab by vm.tab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookingImport by vm.bookingImport.collectAsStateWithLifecycle()
    val usePrefs by vm.usePreferences.collectAsStateWithLifecycle()
    val prefsSummary by vm.preferences.collectAsStateWithLifecycle()
    bookingImport?.let { BookingImportDialog(it, onConfirm = vm::applyBooking, onDismiss = vm::cancelBookingImport) }
    val form by vm.flights.form.collectAsStateWithLifecycle()
    val results by vm.flights.results.collectAsStateWithLifecycle()
    val alerts by vm.flights.alerts.collectAsStateWithLifecycle()
    val checking by vm.flights.checking.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val activityCity by vm.activityCity.collectAsStateWithLifecycle()
    val activityCategory by vm.activityCategory.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val recents by vm.flights.recents.collectAsStateWithLifecycle()
    val activityPlace by vm.activityPlace.collectAsStateWithLifecycle()
    val experience by vm.experience.collectAsStateWithLifecycle()
    val blog by vm.blog.state.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val openTrip by vm.openTrip.collectAsStateWithLifecycle()
    val tripArticle by vm.tripArticle.collectAsStateWithLifecycle()
    val hotels by vm.hotels.state.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val budgetReturn by vm.budgetReturn.collectAsStateWithLifecycle()
    val pendingBooking by vm.hotels.pendingBooking.collectAsStateWithLifecycle()
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
                        selected = tab.section == t,
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
                    onOpenExperience = vm::newEnvolee,
                    onOpenBlog = { vm.selectTab(Tab.BLOG) },
                    onOpenHotels = { vm.hotels.openHotels(null, null, null, null) },
                    onOpenBudget = { vm.selectTab(Tab.BUDGET) },
                    trips = trips,
                    onOpenTrip = vm::openTrip,
                    onOpenAlerts = { vm.selectTab(Tab.ALERTS) },
                    onOpenSettings = configure,
                    onRecent = vm.flights::applyRecent,
                    onClearRecents = vm::clearRecents,
                    onDestination = vm.flights::pickDestination,
                    wiki = vm.core::wikiSummary,
                    weather = vm.core::weatherFor,
                )

                Tab.FLIGHTS -> if (results.visible) {
                    BackHandler { vm.flights.closeResults() }
                    FlightResultsScreen(
                        state = results,
                        onBack = vm.flights::closeResults,
                        onRetry = vm.flights::search,
                        onSort = vm.flights::setSort,
                        onPickDay = vm.flights::pickCalendarDay,
                        onBook = { f -> openUrl(context, vm.flights.bookingUrl(f)) },
                        onActivities = vm::activitiesAtDestination,
                        onPlanStay = { vm.planStayFromFlight(null) },
                        onHotels = {
                            val q = results.query
                            vm.hotels.openHotels(q?.destination?.name, q?.destination, q?.departure,
                                if (q?.roundTrip == true && q.flexibleMonth != true) q.returnDate else null, q?.adults)
                        },
                        onSaveFlight = vm.flights::saveFlight,
                        onFollow = {
                            onFollowRequested()
                            vm.flights.followCurrentSearch()
                        },
                        onOpenSite = { vm.flights.siteSearchUrl()?.let { openUrl(context, it) } },
                        onConfigure = configure,
                    )
                } else {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    FlightSearchScreen(
                        form = form,
                        demo = settings.isDemo,
                        onUpdate = vm.flights::updateForm,
                        onSwap = vm.flights::swapPlaces,
                        onSearch = vm.flights::search,
                        suggest = vm.core::suggestPlaces,
                        onConfigure = configure,
                        recents = recents,
                        onRecent = vm.flights::applyRecent,
                    )
                }

                Tab.CITY, Tab.ACTIVITIES, Tab.BLOG -> {
                    BackHandler {
                        when {
                            citySection == CitySection.ARTICLES && (blog.article != null || blog.articleLoading) -> vm.blog.closeArticle()
                            citySection == CitySection.ARTICLES && blog.city != null -> vm.blog.clearBlogCity()
                            else -> vm.selectTab(Tab.HOME)
                        }
                    }
                    CityScreen(
                        section = citySection,
                        onSection = vm::setCitySection,
                        city = activityCity,
                        selected = activityPlace,
                        onCity = vm::chooseCity,
                        onCityText = vm::setActivityCity,
                        suggest = vm.core::suggestCities,
                        places = {
                            ActivitiesScreen(
                                embedded = true,
                                city = activityCity,
                                category = activityCategory,
                                places = places,
                                onCity = vm::setActivityCity,
                                selectedPlace = activityPlace,
                                onPlace = vm::setActivityPlace,
                                suggestCities = vm.core::suggestCities,
                                onCategory = vm::setActivityCategory,
                                onSearch = { vm.searchActivities(force = true) },
                                wiki = vm.core::wikiSummary,
                                weather = vm.core::weatherFor,
                                isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                                onFavorite = { p -> vm.core.toggleFavorite(p, activityCity.ifBlank { places.city }) },
                                onReadArticle = vm::readPlaceArticle,
                            )
                        },
                        articles = {
                            BlogScreen(
                                embedded = true,
                                state = blog,
                                context = vm.blogContext(),
                                onCity = { c, why -> vm.blog.openBlogCity(c, why); vm.setActivityCity(c.name) },
                                onPlace = vm.blog::openBlogPlace,
                                onClearCity = vm.blog::clearBlogCity,
                                onFilter = vm.blog::setBlogFilter,
                                onArticle = vm.blog::openArticle,
                                onCloseArticle = vm.blog::closeArticle,
                                suggestCities = vm.core::suggestCities,
                                searchArticles = vm.blog::searchArticles,
                                wiki = vm.core::wikiSummary,
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
                        onAnswers = vm::answer,
                        onStep = vm::quizStep,
                        onGenerate = vm::generateExperience,
                        onRegenerate = vm::regenerateExperience,
                        onEdit = vm::editExperience,
                        suggestCities = vm.core::suggestCities,
                        onSave = vm::saveExperience,
                        alternatives = vm::stepAlternatives,
                        onHotels = {
                            val a = experience.answers
                            vm.hotels.openHotels(a.city, a.place, a.startDate, a.startDate.plusDays(a.duration.days.toLong()))
                        },
                        onReplace = vm::replaceExperienceStep,
                        onBudget = vm::openBudgetForExperience,
                        wiki = vm.core::wikiSummary,
                        isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                        onFavorite = { p -> vm.core.toggleFavorite(p, experience.result?.city ?: experience.answers.city) },
                        onReadArticle = vm::readPlaceArticle,
                        edits = ExperienceEdits(
                            move = vm::moveExperienceStep, add = vm::addExperienceStep, refine = vm::refineExperience,
                            detailed = { wasQuick -> vm.setQuickQuiz(!wasQuick) },
                            linked = trips.any { it.id == experience.tripId && it.city.equals(experience.result?.city ?: experience.answers.city, true) },
                            openTrip = { experience.tripId?.let(vm::openTrip) },
                            describe = if (settings.claudeDescribe) vm::describeTrip else null,
                            setTime = vm::setExperienceStepTime,
                            newEnvolee = vm::newEnvolee,
                            prefsSummary = prefsSummary,
                            usePrefs = usePrefs,
                            setUsePrefs = vm::setUsePreferences,
                            askChange = if (settings.has(com.riad.voyage.data.AiFeature.EDIT)) vm::askChange else null,
                            undo = vm::undoChange,
                            answerQuestion = vm::answerQuestion, dismissQuestion = vm::dismissQuestion,
                            importDocs = if (settings.has(com.riad.voyage.data.AiFeature.IMPORT)) { uris -> vm.importDocuments(uris, plan = true) } else null,
                            recapStep = vm::editRecapStep, recapDone = vm::doneRecapStep, recapApply = vm::applyRecap, recapClose = vm::closeRecap,
                        ),
                    )
                }

                Tab.HOTELS -> {
                    val detail = hotels.detail
                    BackHandler { if (detail != null) vm.hotels.closeHotelDetail() else vm.selectTab(Tab.HOME) }
                    if (detail != null) {
                        HotelDetailScreen(
                            state = hotels,
                            d = detail,
                            sandbox = LitePayment.isSandbox(settings.liteApiKey),
                            onBack = vm.hotels::closeHotelDetail,
                            onBook = vm.hotels::startHotelBooking,
                            onConfirm = vm.hotels::confirmHotelBooking,
                            onCancelBooking = vm.hotels::cancelHotelBooking,
                            onPaymentDone = vm.hotels::hotelPaymentDone,
                            paidPending = pendingBooking != null,
                            onRetryPending = vm.hotels::retryPendingBooking,
                            onPaymentCancel = vm.hotels::hotelPaymentCancelled,
                        )
                    } else {
                        HotelsScreen(
                            state = hotels,
                            hasKey = settings.liteApiKey.isNotBlank(),
                            onUpdate = vm.hotels::updateHotels,
                            onSearch = vm.hotels::searchHotels,
                            onSave = vm.hotels::saveHotel,
                            isFavorite = { name -> trips.any { tr -> tr.places.any { it.name.equals(name, ignoreCase = true) } } },
                            suggestCities = vm.core::suggestCities,
                            onConfigure = configure,
                            onOpen = vm.hotels::openHotelDetail,
                            onBookInApp = vm.hotels::openHotelDetail,
                            pendingHotel = pendingBooking?.hotelName,
                            onRetryPending = vm.hotels::retryPendingBooking,
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
                        wiki = vm.core::wikiSummary,
                        weather = vm.core::weatherFor,
                        onReadArticle2 = vm::readPlaceArticle,
                        onBudget = vm::openBudgetForTrip,
                        onHotels = { tr ->
                            vm.hotels.openHotels(tr.city, null, tr.start, tr.end)
                        },
                        alertCount = alerts.size,
                        actions = TripActions(
                            moveStep = vm::moveStep, deleteStep = vm::deleteStep, editStep = vm::editStep, addStep = vm::addStep,
                            addDay = vm::addDay, packing = vm::packingFor, togglePacked = vm::togglePacked,
                            addPackItem = vm::addPackItem, removePackItem = vm::removePackItem, practical = vm::openPractical,
                            create = vm::createTrip, updateInfo = vm::updateTripInfo, plan = vm::planTrip, suggestCities = vm.core::suggestCities,
                            pdf = vm::tripPdf,
                            importBooking = if (settings.has(com.riad.voyage.data.AiFeature.IMPORT)) { uris -> vm.importDocuments(uris) } else null,
                            souvenir = if (settings.has(com.riad.voyage.data.AiFeature.SOUVENIR)) vm::souvenir else null,
                            open = vm::selectTab,
                        ),
                    )
                }

                Tab.BUDGET -> {
                    BackHandler { if (budget != null) vm.closeBudget() else vm.selectTab(Tab.TRIPS) }
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
                            onAdvice = if (settings.has(com.riad.voyage.data.AiFeature.BUDGET)) vm::budgetAdvice else null,
                        )
                    }
                }

                Tab.ALERTS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    AlertsScreen(alerts, settings.isDemo, checking, vm.flights::checkAlertsNow, vm.flights::removeAlert, configure)
                }

                Tab.SETTINGS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    SettingsScreen(settings, keyStatus, vm::saveSettings, vm.quotas, vm::setQuotaLimit, vm::setLanguage, vm::diagnosticReport,
                        onClearHistory = vm::clearRecents, usePrefs = usePrefs, onUsePrefs = vm::setUsePreferences,
                        onGeminiModels = vm::refreshGeminiModels)
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

/** Billet lu par Envolée AI : récapitulatif à confirmer avant de remplir le voyage. */
@androidx.compose.runtime.Composable
private fun BookingImportDialog(state: com.riad.voyage.BookingImport, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(t("Réservation")) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                when {
                    state.loading -> androidx.compose.material3.Text(t("Envolée AI lit votre réservation…"))
                    state.error != null -> androidx.compose.material3.Text(state.error)
                    else -> state.booking?.let { b ->
                        b.segments.forEach { s ->
                            androidx.compose.material3.Text("✈️ ${s.fromCity} → ${s.toCity} · ${s.depDate?.let(::formatDay).orEmpty()} · " +
                                "${com.riad.voyage.data.hhmm(s.depTime) ?: "?"} → ${com.riad.voyage.data.hhmm(s.arrTime) ?: "?"} · ${s.number}")
                        }
                        b.hotel?.let { h ->
                            androidx.compose.material3.Text("🏨 ${h.name} · ${h.checkIn?.let(::formatDay).orEmpty()} → ${h.checkOut?.let(::formatDay).orEmpty()}" +
                                (h.price?.let { " · 💶 ${it.toInt()} ${h.currency}" } ?: ""))
                        }
                        if (b.segments.isNotEmpty()) androidx.compose.material3.Text("👥 " + tf("{0} voyageur(s)", b.passengers) +
                            (b.price?.let { " · 💶 ${it.toInt()} ${b.currency}" } ?: "") + (b.reference.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                        androidx.compose.material3.Text(tf("Le voyage « {0} » sera rempli avec ces dates et ces heures.", t(b.destination)),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (state.booking != null) androidx.compose.material3.TextButton(onClick = onConfirm) { androidx.compose.material3.Text(t("Ajouter à mon voyage")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text(t("Annuler")) } },
    )
}
