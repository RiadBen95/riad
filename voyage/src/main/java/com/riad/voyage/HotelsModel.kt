package com.riad.voyage

import com.riad.voyage.data.HotelOffer
import com.riad.voyage.data.LiteApi
import com.riad.voyage.data.LiteApiMore
import com.riad.voyage.data.LitePayment
import com.riad.voyage.data.PendingBooking
import com.riad.voyage.data.PendingBookingStore
import com.riad.voyage.data.Place
import com.riad.voyage.data.RoomOffer
import com.riad.voyage.data.SavedPlace
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.ui.formatPrice
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Hôtels : recherche LiteAPI, fiche, réservation directe et paiement, hôtels gardés. */
class HotelsModel(private val core: AppCore) {
    private val pendingStore = PendingBookingStore(core.app)
    private val _pendingBooking = MutableStateFlow(pendingStore.load())
    /** Réservation payée en attente de confirmation (à finaliser). */
    val pendingBooking: StateFlow<PendingBooking?> = _pendingBooking.asStateFlow()

    private val _hotels = MutableStateFlow(HotelState())
    val state: StateFlow<HotelState> = _hotels.asStateFlow()

    fun reset() { _hotels.value = HotelState() }

    /** Ouvre les hôtels pour une ville et des dates (depuis un vol, une expérience ou un voyage). */
    fun openHotels(city: String?, place: Place?, checkIn: LocalDate?, checkOut: LocalDate?, adults: Int? = null) {
        _hotels.update { h ->
            val ci = checkIn ?: h.checkIn
            val co = (checkOut ?: ci.plusDays(h.nights)).let { if (it.isAfter(ci)) it else ci.plusDays(1) }
            val sameCity = city.isNullOrBlank() || city.equals(h.city, ignoreCase = true)
            h.copy(
                city = city?.takeIf { it.isNotBlank() } ?: h.city, place = place ?: h.place.takeIf { sameCity },
                checkIn = ci, checkOut = co, adults = adults ?: h.adults,
                offers = if (sameCity && ci == h.checkIn && co == h.checkOut) h.offers else emptyList(),
                searched = sameCity && ci == h.checkIn && co == h.checkOut && h.searched,
            )
        }
        core.tab.value = Tab.HOTELS
        if (!_hotels.value.searched && _hotels.value.city.isNotBlank() && core.settings.value.liteApiKey.isNotBlank()) searchHotels()
    }

    fun updateHotels(transform: (HotelState) -> HotelState) = _hotels.update { h ->
        val n = transform(h)
        val fixed = if (!n.checkOut.isAfter(n.checkIn)) n.copy(checkOut = n.checkIn.plusDays(1)) else n
        if (fixed.city != h.city || fixed.checkIn != h.checkIn || fixed.checkOut != h.checkOut || fixed.adults != h.adults)
            fixed.copy(searched = false, offers = emptyList(), error = null) else fixed
    }

    fun searchHotels() {
        val h = _hotels.value
        val key = core.settings.value.liteApiKey
        if (h.city.isBlank()) return
        if (key.isBlank()) { _hotels.update { it.copy(searched = true) }; return }
        _hotels.update { it.copy(loading = true, error = null) }
        core.scope.launch {
            val geo = core.resolveCity(h.city, h.place?.takeIf { it.name.equals(h.city, ignoreCase = true) })
            if (geo == null) {
                _hotels.update { it.copy(loading = false, searched = true, error = t("Ville introuvable : choisissez-la dans la liste proposée.")) }
                return@launch
            }
            val nationality = java.util.Locale.getDefault().country.ifBlank { "FR" }
            val result = withTimeoutOrNull(35_000) {
                runCatching {
                    if (h.aiQuery.isNotBlank())
                        LiteApi.aiSearch(key, h.aiQuery, h.city, geo, h.checkIn, h.checkOut, h.adults, core.settings.value.currency, nationality)
                    else LiteApi.search(key, geo, h.checkIn, h.checkOut, h.adults, core.settings.value.currency, nationality)
                }
            }
            _hotels.update {
                when {
                    result == null -> it.copy(loading = false, searched = true, error = t("Le service d'hôtels ne répond pas. Réessayez dans un instant."))
                    result.isFailure -> it.copy(loading = false, searched = true, error = TravelpayoutsApi.userMessage(result.exceptionOrNull()!!))
                    else -> it.copy(loading = false, searched = true, offers = result.getOrDefault(emptyList()), sort = HotelSort.BEST,
                        error = if (result.getOrDefault(emptyList()).isEmpty()) t("Aucun hôtel disponible pour ces dates.") else null)
                }
            }
        }
    }

    private val nationality get() = java.util.Locale.getDefault().country.ifBlank { "FR" }

    /** Ouvre la page d'un hôtel : fiche, avis et toutes les offres, chargés en parallèle. */
    fun openHotelDetail(o: HotelOffer) {
        val key = core.settings.value.liteApiKey
        val h = _hotels.value
        _hotels.update { it.copy(detail = HotelDetailState(o)) }
        if (key.isBlank()) { _hotels.update { it.copy(detail = it.detail?.copy(loading = false)) }; return }
        core.scope.launch {
            val more = LiteApiMore
            val info = async { attempt(20_000) { more.detail(key, o.id) }?.getOrNull() }
            val reviews = async { attempt(20_000) { more.reviews(key, o.id) }?.getOrNull() }
            val offers = async {
                attempt(25_000) { more.offers(key, o.id, h.checkIn, h.checkOut, h.adults, core.settings.value.currency, nationality) }
            }
            val off = offers.await()
            val rev = reviews.await()
            _hotels.update { st ->
                if (st.detail?.hotel?.id != o.id) st else st.copy(
                    detail = st.detail!!.copy(
                        loading = false, info = info.await(), reviews = rev?.first.orEmpty(), summary = rev?.second,
                        offers = off?.getOrNull().orEmpty(),
                        error = off?.exceptionOrNull()?.let { TravelpayoutsApi.userMessage(it) },
                    ),
                )
            }
        }
    }

    fun closeHotelDetail() = _hotels.update { it.copy(detail = null) }

    /** Étape 1 de la réservation directe : LiteAPI confirme la disponibilité et le prix exact. */
    fun startHotelBooking(offer: RoomOffer) {
        val key = core.settings.value.liteApiKey
        // Même chambre déjà pré-réservée : on la réutilise (pas de seconde pré-réservation ni de doublon).
        _hotels.value.detail?.let { d ->
            if (d.prebook != null && d.booking?.offerId == offer.offerId && d.confirmed == null) {
                _hotels.update { it.copy(detail = d.copy(booking = offer, bookingError = null)) }
                return
            }
        }
        _hotels.update { it.copy(detail = it.detail?.copy(booking = offer, prebook = null, bookingBusy = true, bookingError = null, confirmed = null)) }
        core.scope.launch {
            val r = attempt(25_000) { LiteApiMore.prebook(key, offer.offerId) }
            _hotels.update { st ->
                val d = st.detail ?: return@update st
                st.copy(detail = when {
                    r == null -> d.copy(bookingBusy = false, bookingError = t("Le service d'hôtels ne répond pas. Réessayez dans un instant."))
                    r.isFailure -> d.copy(bookingBusy = false, bookingError = TravelpayoutsApi.userMessage(r.exceptionOrNull()!!))
                    r.getOrNull() == null -> d.copy(bookingBusy = false, bookingError = t("Cette offre n'est plus disponible. Choisissez-en une autre."))
                    else -> d.copy(bookingBusy = false, prebook = r.getOrNull())
                })
            }
        }
    }

    /**
     * Étape 2 : coordonnées du voyageur, puis paiement sécurisé LiteAPI (carte saisie sur la page
     * LiteAPI, jamais dans l'app). En mode test sans page de paiement : paiement fictif.
     */
    fun confirmHotelBooking(firstName: String, lastName: String, email: String) {
        val key = core.settings.value.liteApiKey
        val d = _hotels.value.detail ?: return
        val pre = d.prebook ?: return
        val guest = listOf(firstName.trim(), lastName.trim(), email.trim())
        val sandbox = LitePayment.isSandbox(key)
        when {
            pre.secretKey != null && pre.transactionId != null -> _hotels.update {
                it.copy(detail = it.detail?.copy(guest = guest, bookingError = null,
                    paymentHtml = LitePayment.html(pre.secretKey, sandbox)))
            }
            sandbox -> bookHotel(guest, null)
            else -> _hotels.update {
                it.copy(detail = it.detail?.copy(bookingError = t("Le paiement sécurisé n'est pas disponible pour cette offre. Réservez chez un partenaire.")))
            }
        }
    }

    /** Retour de la page de paiement : paiement accepté → réservation. */
    fun hotelPaymentDone() {
        val d = _hotels.value.detail ?: return
        _hotels.update { it.copy(detail = it.detail?.copy(paymentHtml = null)) }
        bookHotel(d.guest, d.prebook?.transactionId)
    }

    fun hotelPaymentCancelled() = _hotels.update { it.copy(detail = it.detail?.copy(paymentHtml = null)) }

    private fun bookHotel(guest: List<String>, transactionId: String?) {
        val d = _hotels.value.detail ?: return
        val pre = d.prebook ?: return
        if (guest.size < 3) return
        val h = _hotels.value
        val p = PendingBooking(
            pre.prebookId, transactionId.orEmpty(), guest, h.city, h.checkIn.toString(), h.checkOut.toString(),
            d.hotel.name, d.hotel.lat ?: 0.0, d.hotel.lon ?: 0.0, pre.price ?: d.booking?.price, pre.currency ?: d.booking?.currency,
        )
        // Paiement fait : on garde une trace tant que la réservation n'est pas confirmée.
        if (transactionId != null) { pendingStore.save(p); _pendingBooking.value = p }
        runBooking(p)
    }

    /** Finalise une réservation déjà payée (même paiement, aucun nouveau débit). */
    fun retryPendingBooking() { _pendingBooking.value?.let { runBooking(it) } }

    private fun runBooking(p: PendingBooking) {
        val key = core.settings.value.liteApiKey
        val paid = p.transactionId.isNotBlank()
        val (firstName, lastName, email) = p.guest
        _hotels.update { it.copy(detail = it.detail?.copy(bookingBusy = true, bookingError = null)) }
        core.scope.launch {
            val r = attempt(40_000) { LiteApiMore.book(key, p.prebookId, firstName, lastName, email, p.transactionId.ifBlank { null }) }
            val ok = r?.getOrNull()
            val failure = when {
                ok != null -> null
                paid -> t("Paiement reçu, mais la réservation n'est pas encore confirmée. Réessayez : aucun nouveau paiement ne sera demandé.")
                r == null -> t("Le service d'hôtels ne répond pas. Réessayez dans un instant.")
                else -> r.exceptionOrNull()?.let { TravelpayoutsApi.userMessage(it) } ?: t("La réservation n'a pas abouti.")
            }
            _hotels.update { st -> st.copy(detail = st.detail?.copy(bookingBusy = false, confirmed = ok, bookingError = failure)) }
            if (_hotels.value.detail == null && failure != null) core.message.value = failure
            if (ok == null) return@launch
            pendingStore.clear()
            _pendingBooking.value = null
            val trip = core.tripFor(p.city)
            val price = (ok.price ?: p.price)?.let { com.riad.voyage.ui.formatPrice(it.toInt(), ok.currency ?: p.currency ?: "EUR") }
            val ref = if (com.riad.voyage.data.LitePayment.isSandbox(key)) t("Réservation test (non valable)") else "Réf. " + (ok.confirmation ?: ok.bookingId)
            val label = listOfNotNull("Hôtel réservé", ref, price).joinToString(" · ")
            core.saveTrip(
                trip.copy(
                    places = trip.places + SavedPlace(p.hotelName, label, p.lat, p.lon, p.lat != 0.0),
                    startDate = trip.startDate ?: p.checkIn, endDate = trip.endDate ?: p.checkOut,
                ),
                tf("Réservation ajoutée à votre voyage : {0}", t(p.city)),
            )
        }
    }

    /** Ferme la fenêtre ; la pré-réservation reste valable pour la même chambre (sauf si déjà confirmée). */
    fun cancelHotelBooking() = _hotels.update {
        it.copy(detail = it.detail?.let { d -> d.copy(booking = null, prebook = d.prebook.takeIf { d.confirmed == null }, bookingError = null, confirmed = null) })
    }

    fun saveHotel(o: HotelOffer) {
        val h = _hotels.value
        val price = o.totalPrice?.let { com.riad.voyage.ui.formatPrice(it.toInt(), o.currency) }.orEmpty()
        core.toggleFavorite(o.toPoi(price), h.city)
    }

}
