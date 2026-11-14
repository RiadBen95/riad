package com.riad.voyage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.riad.voyage.HotelDetailState
import com.riad.voyage.HotelState
import com.riad.voyage.data.HotelBooking
import com.riad.voyage.data.RoomOffer
import com.riad.voyage.t
import com.riad.voyage.tf

/** Page complète d'un hôtel (LiteAPI) : photos, description, équipements, avis, chambres et réservation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotelDetailScreen(
    state: HotelState,
    d: HotelDetailState,
    sandbox: Boolean,
    onBack: () -> Unit,
    onBook: (RoomOffer) -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onCancelBooking: () -> Unit,
    onPaymentDone: () -> Unit = {},
    onPaymentCancel: () -> Unit = {},
    paidPending: Boolean = false,
    onRetryPending: () -> Unit = {},
) {
    val context = LocalContext.current
    val info = d.info
    val images = info?.images?.takeIf { it.isNotEmpty() } ?: listOfNotNull(d.hotel.photo)
    var showAll by remember { mutableStateOf(false) }

    if (d.paymentHtml != null) PaymentDialog(d.paymentHtml, sandbox, onPaymentDone, onPaymentCancel)
    else if (d.booking != null) BookingDialog(d, state, sandbox, onConfirm, onCancelBooking, paidPending, onRetryPending)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(images.take(15)) { url ->
                        AsyncImage(model = url, contentDescription = d.hotel.name, imageLoader = Images.get(context),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (images.size == 1) 420.dp else 300.dp).height(230.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer))
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Text(t("← Retour"), color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info?.name ?: d.hotel.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                (info?.stars ?: d.hotel.stars)?.let { Text("★".repeat(it), color = com.riad.voyage.ui.theme.BrandCoral) }
                (info?.rating ?: d.hotel.rating)?.let { r ->
                    Text("${"%.1f".format(r)}/10" + ((info?.reviews ?: d.hotel.reviews)?.let { " · " + tf("{0} avis", it) } ?: ""),
                        fontWeight = FontWeight.Bold, color = com.riad.voyage.ui.theme.dealColor())
                }
                (info?.address ?: d.hotel.address)?.let { Text("📍 $it", style = MaterialTheme.typography.bodyMedium) }
                if (info?.checkin != null || info?.checkout != null) {
                    Text("🕒 " + listOfNotNull(info.checkin?.let { tf("Arrivée dès {0}", it) }, info.checkout?.let { tf("Départ avant {0}", it) })
                        .joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
                Text(tf("Du {0} au {1}", formatDay(state.checkIn), formatDay(state.checkOut)) + " · " + tf("{0} nuit(s)", state.nights) +
                    " · " + tf("{0} voyageur(s)", state.adults), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (d.loading) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        // Chambres et offres
        item { SectionTitle2(t("Chambres disponibles")) }
        d.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) } }
        if (!d.loading && d.offers.isEmpty() && d.error == null) {
            item { Text(t("Aucune chambre disponible pour ces dates."), modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        items(d.offers.take(12), key = { it.offerId }) { o ->
            OfferCard(o, state, d.hotel.name, sandbox, { onBook(o) })
        }

        info?.description?.let { desc ->
            item { SectionTitle2(t("L'hôtel")) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(desc, style = MaterialTheme.typography.bodyMedium, maxLines = if (showAll) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { showAll = !showAll }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showAll) t("Voir moins") else t("Lire la suite"))
                    }
                }
            }
        }
        if (!info?.facilities.isNullOrEmpty()) {
            item { SectionTitle2(t("Équipements")) }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info!!.facilities.take(24).forEach { f ->
                        Text(f, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

        // Avis des clients
        val summary = d.summary
        if (summary != null || d.reviews.isNotEmpty()) {
            item { SectionTitle2(t("Avis des clients")) }
        }
        if (summary != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.categories.take(8).forEach { (name, rating) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(progress = { (rating / 10.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text("%.1f".format(rating), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (summary.pros.isNotEmpty()) Text("👍 " + summary.pros.take(5).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (summary.cons.isNotEmpty()) Text("👎 " + summary.cons.take(5).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(d.reviews.take(6)) { r ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        r.score?.let { Text("%.1f".format(it), fontWeight = FontWeight.Bold, color = com.riad.voyage.ui.theme.dealColor()) }
                        Spacer(Modifier.width(8.dp))
                        Text(listOfNotNull(r.name, r.country?.uppercase(), r.date).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    r.headline?.let { Text(it, fontWeight = FontWeight.Bold) }
                    r.pros?.let { Text("👍 $it", style = MaterialTheme.typography.bodySmall) }
                    r.cons?.let { Text("👎 $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        info?.important?.let { imp ->
            item { SectionTitle2(t("À savoir")) }
            item { Text(imp, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item {
            Text(t("Données hôtels, avis et prix : LiteAPI."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun SectionTitle2(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun OfferCard(o: RoomOffer, state: HotelState, hotelName: String, sandbox: Boolean, onBook: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(o.roomName.ifBlank { t("Chambre") }, fontWeight = FontWeight.Bold)
            val tags = listOfNotNull(o.board, if (o.refundable) t("Annulation gratuite") else t("Non remboursable"))
            Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = if (o.refundable) com.riad.voyage.ui.theme.dealColor() else MaterialTheme.colorScheme.onSurfaceVariant)
            o.cancelDeadline?.takeIf { o.refundable }?.let {
                Text(tf("Annulation gratuite jusqu'au {0}", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatPrice(o.price.toInt(), o.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(tf("{0} / nuit", formatPrice((o.price / state.nights).toInt(), o.currency)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    Button(onClick = { menu = true }) { Text(t("Réserver")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (sandbox) t("Réserver dans l'app (mode test)") else t("Réserver dans l'app")) },
                            onClick = { menu = false; onBook() },
                        )
                        HotelBooking.partners("$hotelName ${state.city}", state.checkIn, state.checkOut, state.adults).forEach { p ->
                            DropdownMenuItem(text = { Text(tf("Réserver sur {0}", p.name)) }, onClick = { menu = false; openUrl(context, p.url) })
                        }
                    }
                }
            }
        }
    }
}

/** Réservation directe : vérification de l'offre, coordonnées du voyageur, confirmation. */
@Composable
private fun BookingDialog(
    d: HotelDetailState,
    state: HotelState,
    sandbox: Boolean,
    onConfirm: (String, String, String) -> Unit,
    onCancel: () -> Unit,
    paidPending: Boolean,
    onRetry: () -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val offer = d.booking ?: return
    val confirmed = d.confirmed
    AlertDialog(
        onDismissRequest = { if (!d.bookingBusy) onCancel() },
        title = { Text(if (confirmed != null) t("Réservation confirmée ✓") else t("Réserver cette chambre")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmed != null) {
                    Text(d.hotel.name, fontWeight = FontWeight.Bold)
                    Text(tf("Du {0} au {1}", formatDay(state.checkIn), formatDay(state.checkOut)))
                    Text(tf("Numéro de réservation : {0}", confirmed.confirmation ?: confirmed.bookingId), fontWeight = FontWeight.Bold)
                    confirmed.status?.let { Text(tf("Statut : {0}", it)) }
                    Text(t("La réservation a été ajoutée à « Mes voyages »."), style = MaterialTheme.typography.bodySmall)
                    if (sandbox) Text(t("Mode test : aucune vraie réservation n'est faite et rien n'est payé."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("${d.hotel.name} · ${offer.roomName}", fontWeight = FontWeight.Bold)
                    val price = d.prebook?.price ?: offer.price
                    Text(tf("Prix confirmé : {0}", formatPrice(price.toInt(), d.prebook?.currency ?: offer.currency)),
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (d.prebook == null && d.bookingBusy) Text(t("Vérification de la disponibilité…"))
                    if (d.prebook != null) {
                        OutlinedTextField(first, { first = it }, label = { Text(t("Prénom")) }, singleLine = true)
                        OutlinedTextField(last, { last = it }, label = { Text(t("Nom")) }, singleLine = true)
                        OutlinedTextField(email, { email = it }, label = { Text(t("E-mail")) }, singleLine = true)
                        if (sandbox) Text(t("Mode test : aucune vraie réservation n'est faite et rien n'est payé."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        else if (d.prebook.secretKey != null) Text(t("Paiement sécurisé par LiteAPI : votre carte est saisie sur leur page, jamais dans l'app."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    d.bookingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (d.bookingBusy) CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            when {
                confirmed != null -> Button(onClick = onCancel) { Text(t("Fermer")) }
                paidPending -> Button(onClick = onRetry, enabled = !d.bookingBusy) { Text(t("Réessayer la réservation")) }
                d.prebook != null -> Button(
                    onClick = { onConfirm(first, last, email) },
                    enabled = !d.bookingBusy && first.isNotBlank() && last.isNotBlank() && email.contains('@'),
                ) { Text(if (d.prebook.secretKey != null) t("Continuer vers le paiement") else t("Confirmer la réservation")) }
                else -> {}
            }
        },
        dismissButton = { if (confirmed == null) OutlinedButton(onClick = onCancel, enabled = !d.bookingBusy) { Text(t("Annuler")) } },
    )
}

/** Page de paiement sécurisé LiteAPI dans une WebView ; le retour sur l'adresse de fin déclenche la réservation. */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PaymentDialog(html: String, sandbox: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }   // erreur de la page de paiement (affichée pour diagnostic)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Paiement sécurisé"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    androidx.compose.material3.TextButton(onClick = onCancel) { Text(t("Annuler")) }
                }
                if (sandbox) Text(t("Mode test : utilisez la carte 4242 4242 4242 4242, une date future et n'importe quel code."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp))
                problem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            // Page verrouillée : JavaScript (nécessaire au formulaire Stripe), mais ni fichiers,
                            // ni fenêtres secondaires, ni contenu non chiffré.
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            // Le formulaire de carte (Stripe) tourne dans des iframes qui ont besoin de leurs cookies.
                            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(m: android.webkit.ConsoleMessage?): Boolean {
                                    if (m?.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR)
                                        problem = m.message().take(200)
                                    return true
                                }
                            }
                            webViewClient = object : android.webkit.WebViewClient() {
                                /** Vrai = navigation bloquée. Seuls LiteAPI, Stripe et l'adresse de fin sont permis. */
                                private fun check(url: String?): Boolean {
                                    if (url != null && url.startsWith(com.riad.voyage.data.LitePayment.RETURN_URL)) {
                                        if (!finished) { finished = true; onDone() }
                                        return true
                                    }
                                    if (url == null || com.riad.voyage.data.LitePayment.allowed(url)) return false
                                    problem = t("Page extérieure bloquée pour votre sécurité.")
                                    return true
                                }
                                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?) =
                                    check(request?.url?.toString())
                                override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?,
                                                             error: android.webkit.WebResourceError?) {
                                    if (request?.isForMainFrame == true || request?.url?.host?.contains("stripe") == true ||
                                        request?.url?.host?.contains("liteapi") == true)
                                        problem = "${request?.url?.host}: ${error?.description}"
                                }
                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    if (check(url)) view?.stopLoading() else super.onPageStarted(view, url, favicon)
                                }
                            }
                            loadDataWithBaseURL("https://payment-wrapper.liteapi.travel/", html, "text/html", "UTF-8", null)
                        }
                    },
                    // Rien ne reste en mémoire après le paiement (cache, historique, page).
                    onRelease = { w -> w.stopLoading(); w.clearCache(true); w.clearHistory(); w.clearFormData(); w.destroy() },
                )
            }
        }
    }
}
