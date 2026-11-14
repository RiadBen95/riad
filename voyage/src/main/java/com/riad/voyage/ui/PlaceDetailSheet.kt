package com.riad.voyage.ui

import ActivityLinks
import ExperienceBuilder
import PlacesApi
import PlacesParser
import PointOfInterest
import WikiSummary
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.PlacesApi
import com.riad.voyage.data.PlacesParser
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.WikiSummary
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.tk

/** Ce qu'on sait d'une étape en plus du lieu lui-même (programme Expérience). */
data class PlaceExtra(val why: String? = null, val time: String? = null, val durationMin: Int = 0)

/**
 * Encart détaillé d'un lieu : photo, description (Wikipédia ou guide), notes, adresse, horaires,
 * contact, temps de visite conseillé et actions (itinéraire, billets, favori, article complet).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceDetailSheet(
    place: PointOfInterest,
    category: ActivityLinks.Category?,
    city: String,
    extra: PlaceExtra = PlaceExtra(),
    wiki: suspend (String) -> WikiSummary?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onReadArticle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var summary by remember(place.id) { mutableStateOf<WikiSummary?>(null) }
    var loading by remember(place.id) { mutableStateOf(place.wikipedia != null) }
    LaunchedEffect(place.id) {
        place.wikipedia?.let { summary = wiki(it) }
        loading = false
    }
    val image = place.imageUrl ?: summary?.imageUrl
    val description = summary?.extract ?: place.description

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (image != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    AsyncImage(model = image, contentDescription = place.name, imageLoader = Images.get(context),
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(category?.emoji, tk(place.kind)).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = onFavorite) {
                    Text(if (favorite) "♥" else "♡", style = MaterialTheme.typography.headlineSmall,
                        color = if (favorite) com.riad.voyage.ui.theme.BrandCoral else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Repères rapides : badges, notes, temps de visite, distance.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (place.isMustSee) Chip(t("★ Incontournable"))
                if (place.inGuide) Chip(t("📘 Recommandé par le guide"))
                place.ratings.forEach { r -> Chip("★ ${"%.1f".format(r.rating)} ${r.source.label} · " + tf("{0} avis", r.count)) }
                val minutes = extra.durationMin.takeIf { it > 0 } ?: category?.let { ExperienceBuilder.visitMinutes(place, it) }
                if (minutes != null && !place.isVenue) Chip("⏱ " + tf("Temps de visite conseillé : {0}", ExperienceBuilder.formatDuration(minutes)))
                extra.time?.let { Chip("🕘 " + tf("Prévu à {0}", it)) }
                if (place.distanceM >= 0) Chip("📍 " + tf("à {0} du centre",
                    if (place.distanceM < 1000) "${place.distanceM} m" else "${"%.1f".format(place.distanceM / 1000.0)} km"))
                place.openNow?.let { Chip(if (it) t("Ouvert en ce moment") else t("Fermé en ce moment")) }
            }

            extra.why?.let {
                Text(t("Pourquoi ce lieu"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text(t("Description"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(t("Chargement…"))
                }
                description != null -> Text(description, style = MaterialTheme.typography.bodyLarge)
                else -> Text(t("Aucune description disponible pour ce lieu."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            place.wikipedia?.let { tag ->
                TextButton(onClick = { onReadArticle(tag) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(t("Lire l'article complet ›"))
                }
            }

            // Infos pratiques
            val practical = listOfNotNull(
                place.address?.let { "📍 " to it },
                place.openingHours?.let { "🕒 " to (if (com.riad.voyage.I18n.lang == com.riad.voyage.Lang.FR) PlacesParser.frenchHours(it) else it) },
                place.phone?.let { "📞 " to it },
                place.website?.let { "🌐 " to it.removePrefix("https://").removePrefix("http://").removeSuffix("/") },
            )
            if (practical.isNotEmpty()) {
                HorizontalDivider()
                Text(t("Infos pratiques"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                practical.forEach { (icon, text) -> Text(icon + text, style = MaterialTheme.typography.bodyMedium) }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openUrl(context, ActivityLinks.directionsUrl(place, city)) }) { Text(t("Itinéraire")) }
                when {
                    place.isVenue -> OutlinedButton(onClick = { openUrl(context, ActivityLinks.restaurantUrl(place, city)) }) {
                        Text(t("Réserver / voir"))
                    }
                    !place.isFree -> OutlinedButton(onClick = { openUrl(context, ActivityLinks.ticketUrls(place, city).first().url) }) {
                        Text(t("Billets"))
                    }
                    else -> {}
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                place.phone?.let { phone -> TextButton(onClick = { dialNumber(context, phone) }) { Text(t("Appeler")) } }
                place.website?.let { site -> TextButton(onClick = { openUrl(context, site) }) { Text(t("Site officiel ›")) } }
                place.wikipedia?.let { tag ->
                    PlacesApi.wikiPageUrl(tag)?.let { url ->
                        TextButton(onClick = { openUrl(context, url) }) { Text("Wikipédia ›") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun dialNumber(context: Context, phone: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.replace(" ", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
