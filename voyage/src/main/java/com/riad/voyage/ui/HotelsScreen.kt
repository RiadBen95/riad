package com.riad.voyage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.riad.voyage.HotelSort
import com.riad.voyage.HotelState
import com.riad.voyage.data.HotelBooking
import com.riad.voyage.data.HotelOffer
import com.riad.voyage.data.Place
import com.riad.voyage.t
import com.riad.voyage.tf
import java.time.LocalDate

@Composable
fun HotelsScreen(
    state: HotelState,
    hasKey: Boolean,
    onUpdate: ((HotelState) -> HotelState) -> Unit,
    onSearch: () -> Unit,
    onSave: (HotelOffer) -> Unit,
    isFavorite: (String) -> Boolean,
    suggestCities: suspend (String) -> List<Place>,
    onConfigure: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(t("Hôtels"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(t("Comparez les hôtels de la ville pour vos dates, puis réservez au meilleur prix."),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            PlaceField(
                label = t("Ville"),
                selected = state.place,
                onSelect = { p -> onUpdate { it.copy(city = p.name, place = p) } },
                suggest = suggestCities,
                placeholder = t("Ex. Paris, Alger, Rome…"),
                onTextChange = { txt -> onUpdate { it.copy(city = txt, place = null) } },
                initialText = state.city,
                citiesOnly = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(t("Arrivée"), state.checkIn, LocalDate.now(), { d -> onUpdate { it.copy(checkIn = d) } }, Modifier.weight(1f))
                DateButton(t("Départ"), state.checkOut, state.checkIn.plusDays(1), { d -> onUpdate { it.copy(checkOut = d) } }, Modifier.weight(1f))
            }
        }
        item {
            Stepper(t("Voyageurs (adultes)"), state.adults, 1..8, { n -> onUpdate { it.copy(adults = n) } })
            Text(tf("{0} nuit(s)", state.nights), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = onSearch, enabled = state.city.isNotBlank() && !state.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(t("Rechercher les hôtels"))
            }
        }

        if (!hasKey) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("Prix en direct désactivés"), fontWeight = FontWeight.Bold)
                        Text(t("Ajoutez une clé LiteAPI (gratuite) dans Réglages pour voir ici les hôtels disponibles avec leur prix. En attendant, réservez chez nos partenaires :"),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onConfigure, contentPadding = PaddingValues(0.dp)) { Text(t("Configurer la clé ›")) }
                    }
                }
            }
            if (state.city.isNotBlank()) {
                items(HotelBooking.partners(state.city, state.checkIn, state.checkOut, state.adults)) { p ->
                    PartnerRow(p.name) { openUrl(context, p.url) }
                }
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        state.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }

        if (state.offers.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HotelSort.entries) { s ->
                        FilterChip(selected = state.sort == s, onClick = { onUpdate { it.copy(sort = s) } }, label = { Text(t(s.label)) })
                    }
                }
                Text(tf("{0} hôtels disponibles", state.offers.size), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp))
            }
            items(state.sorted, key = { it.id }) { o -> HotelCard(o, state, isFavorite(o.name), { onSave(o) }) }
            item {
                Text(t("Prix et disponibilités : LiteAPI. La réservation se finalise chez le partenaire choisi, au prix affiché par celui-ci."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerRow(name: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(t("Voir ›"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HotelCard(o: HotelOffer, state: HotelState, favorite: Boolean, onSave: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                o.photo?.let {
                    AsyncImage(model = it, contentDescription = o.name, imageLoader = Images.get(context),
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        o.stars?.let { Text("★".repeat(it), color = com.riad.voyage.ui.theme.BrandCoral) }
                    }
                    TextButton(onClick = onSave) {
                        Text(if (favorite) "♥" else "♡", style = MaterialTheme.typography.titleLarge,
                            color = if (favorite) com.riad.voyage.ui.theme.BrandCoral else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                o.rating?.let { r ->
                    Text(
                        "${"%.1f".format(r)}/10" + (o.reviews?.let { " · " + tf("{0} avis", it) } ?: ""),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = com.riad.voyage.ui.theme.dealColor(),
                    )
                }
                o.distanceM?.let { m ->
                    Text(tf("à {0} du centre", if (m < 1000) "$m m" else "${"%.1f".format(m / 1000.0)} km"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val tags = listOfNotNull(t("Annulation gratuite").takeIf { o.refundable }, o.board)
                if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                o.totalPrice?.let { total ->
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text(formatPrice(total.toInt(), o.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(
                            "  " + tf("{0} nuit(s)", state.nights) + " · " +
                                tf("{0} / nuit", formatPrice((o.perNight(state.nights) ?: 0.0).toInt(), o.currency)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    Button(onClick = { menu = true }) { Text(t("Réserver")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        HotelBooking.partners("${o.name} ${state.city}", state.checkIn, state.checkOut, state.adults).forEach { p ->
                            DropdownMenuItem(text = { Text(tf("Réserver sur {0}", p.name)) }, onClick = { menu = false; openUrl(context, p.url) })
                        }
                    }
                }
            }
        }
    }
}
