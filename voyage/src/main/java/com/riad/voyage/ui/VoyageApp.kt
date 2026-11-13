package com.riad.voyage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    val hotel by vm.hotel.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val activityCity by vm.activityCity.collectAsStateWithLifecycle()
    val activityCategory by vm.activityCategory.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
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
        bottomBar = {
            NavigationBar {
                Tab.entries.filter { it.inBottomBar }.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { vm.selectTab(t) },
                        icon = { Icon(tabIcon(t), contentDescription = t.label) },
                        label = { Text(t.label, maxLines = 1, fontSize = 11.sp) },
                    )
                }
            }
        },
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
                    onOpenHotels = { vm.selectTab(Tab.HOTELS) },
                    onOpenActivities = { vm.selectTab(Tab.ACTIVITIES) },
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
                        onHotels = vm::hotelsFor,
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

                Tab.HOTELS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    HotelScreen(hotel, vm::updateHotel) {
                        vm.setActivityCity(hotel.city)
                        vm.selectTab(Tab.ACTIVITIES)
                    }
                }

                Tab.ACTIVITIES -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    ActivitiesScreen(
                        city = activityCity,
                        category = activityCategory,
                        places = places,
                        onCity = vm::setActivityCity,
                        onCategory = vm::setActivityCategory,
                        onSearch = { vm.searchActivities(force = true) },
                        wiki = vm::wikiSummary,
                    )
                }

                Tab.ALERTS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    AlertsScreen(alerts, settings.isDemo, checking, vm::checkAlertsNow, vm::removeAlert, configure)
                }

                Tab.SETTINGS -> {
                    BackHandler { vm.selectTab(Tab.HOME) }
                    SettingsScreen(settings, keyStatus, vm::saveSettings)
                }
            }
        }
    }
}

private fun tabIcon(t: Tab) = when (t) {
    Tab.HOME -> AppIcons.Home
    Tab.FLIGHTS -> AppIcons.Flight
    Tab.HOTELS -> AppIcons.Hotel
    Tab.ACTIVITIES -> AppIcons.Ticket
    Tab.ALERTS -> AppIcons.Bell
    Tab.SETTINGS -> AppIcons.Settings
}
