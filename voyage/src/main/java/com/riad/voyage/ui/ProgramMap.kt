package com.riad.voyage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riad.voyage.t
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Une étape placée sur la carte. */
data class MapStop(val name: String, val lat: Double, val lon: Double)

/**
 * Carte OpenStreetMap de l'itinéraire : étapes numérotées dans l'ordre de la journée, reliées
 * par un trait. Une puce par jour quand le programme en compte plusieurs.
 */
@Composable
fun ProgramMap(days: List<Pair<String, List<MapStop>>>, modifier: Modifier = Modifier) {
    val usable = days.map { (title, stops) -> title to stops.filter { it.lat != 0.0 || it.lon != 0.0 } }.filter { it.second.isNotEmpty() }
    if (usable.isEmpty()) return
    var selected by remember(usable.size) { mutableIntStateOf(0) }
    val day = selected.coerceIn(usable.indices)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (usable.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(usable.size) { i ->
                    FilterChip(selected = i == day, onClick = { selected = i }, label = { Text(usable[i].first) })
                }
            }
        }
        val stops = usable[day].second
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
                    osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
                }
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    overlays.add(CopyrightOverlay(ctx))
                    // La carte garde les gestes (sinon la liste défile à la place).
                    setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                }
            },
            update = { map -> show(map, stops) },
            onRelease = { it.onDetach() },
        )
        Text(t("Carte : © contributeurs OpenStreetMap"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun show(map: MapView, stops: List<MapStop>) {
    map.overlays.removeAll { it is Marker || it is Polyline }
    val points = stops.map { GeoPoint(it.lat, it.lon) }
    if (points.size > 1) {
        map.overlays.add(Polyline(map).apply {
            setPoints(points)
            outlinePaint.color = 0xFF2E6BE6.toInt()
            outlinePaint.strokeWidth = 8f
        })
    }
    stops.forEachIndexed { i, s ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(s.lat, s.lon)
            title = "${i + 1}. ${s.name}"
            setTextIcon("${i + 1}")
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        })
    }
    map.invalidate()
    val fit = {
        if (points.size == 1) {
            map.controller.setZoom(15.0)
            map.controller.setCenter(points[0])
        } else {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.3f), false)
        }
    }
    if (map.width > 0) fit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
}
