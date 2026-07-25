package com.segurancarural.gpstracker.util

import com.google.gson.JsonObject
import com.segurancarural.gpstracker.ui.model.DeviceMapStyle
import com.segurancarural.gpstracker.ui.model.MapDisplayData
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val SOURCE_ROUTE = "route-source"
const val LAYER_ROUTE = "route-layer"
const val SOURCE_MARKER = "marker-source"
const val LAYER_MARKER_CIRCLE = "marker-circle-layer"
const val LAYER_MARKER_LABEL = "marker-label-layer"
const val SOURCE_SOS = "sos-source"
const val LAYER_SOS = "sos-layer"
const val SosRedHex = "#DC2626"

const val SOURCE_ACCURACY = "accuracy-source"
const val LAYER_ACCURACY_FILL = "accuracy-fill-layer"
const val LAYER_ACCURACY_BORDER = "accuracy-border-layer"

const val SOURCE_FAMILY_ROUTES = "family-routes-source"
const val LAYER_FAMILY_ROUTES = "family-routes-layer"

const val SOURCE_ARROWS = "arrows-source"
const val LAYER_ARROWS = "arrows-layer"

fun createAccuracyPolygon(center: Point, radiusMeters: Double): Polygon {
    val points = mutableListOf<Point>()
    val earthRadius = 6378137.0
    val numPoints = 64
    val lat = center.latitude()
    val lng = center.longitude()

    for (i in 0..numPoints) {
        val angle = 2 * PI * i / numPoints
        val dy = radiusMeters * cos(angle)
        val dx = radiusMeters * sin(angle)
        
        val pLat = lat + (dy / earthRadius) * (180 / PI)
        val pLng = lng + (dx / earthRadius) * (180 / PI) / cos(lat * PI / 180)
        points.add(Point.fromLngLat(pLng, pLat))
    }
    return Polygon.fromLngLats(listOf(points))
}

fun getSatelliteStyleJson(): String {
    return """
    {
      "version": 8,
      "glyphs": "https://basemaps.cartocdn.com/fonts/{fontstack}/{range}.pbf",
      "sources": {
        "satellite-tiles": {
          "type": "raster",
          "tiles": [
            "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}&scale=2"
          ],
          "tileSize": 256,
          "attribution": "© Google"
        }
      },
      "layers": [
        {
          "id": "satellite-layer",
          "type": "raster",
          "source": "satellite-tiles",
          "minzoom": 0,
          "maxzoom": 22
        }
      ]
    }
    """.trimIndent()
}

fun updateMapLayers(
    style: Style,
    displayData: MapDisplayData,
    localStyle: DeviceMapStyle,
) {
    val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE) ?: return
    val markerSource = style.getSourceAs<GeoJsonSource>(SOURCE_MARKER) ?: return
    val sosSource = style.getSourceAs<GeoJsonSource>(SOURCE_SOS) ?: return
    val accuracySource = style.getSourceAs<GeoJsonSource>(SOURCE_ACCURACY) ?: return
    val familyRoutesSource = style.getSourceAs<GeoJsonSource>(SOURCE_FAMILY_ROUTES) ?: return
    val arrowsSource = style.getSourceAs<GeoJsonSource>(SOURCE_ARROWS) ?: return

    val markerFeatures = mutableListOf<Feature>()
    val sosFeatures = mutableListOf<Feature>()
    val accuracyFeatures = mutableListOf<Feature>()
    val familyRouteFeatures = mutableListOf<Feature>()
    val arrowFeatures = mutableListOf<Feature>()

    if (displayData.isFamilyMode) {
        routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        
        displayData.familyMarkers.forEach { marker ->
            val props = JsonObject().apply {
                addProperty("deviceId", marker.deviceId)
                addProperty("label", marker.markerLetter)
                addProperty("color", marker.markerColorHex)
            }
            markerFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat), props))

            if (marker.accuracy > 0.0) {
                val accPolygon = createAccuracyPolygon(Point.fromLngLat(marker.lng, marker.lat), marker.accuracy)
                val accProps = JsonObject().apply { addProperty("color", marker.markerColorHex) }
                accuracyFeatures.add(Feature.fromGeometry(accPolygon, accProps))
            }

            if (marker.emergencyState) {
                sosFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat)))
            }

            val prev = marker.previousLocations
            if (!prev.isNullOrEmpty()) {
                val points = prev.map { Point.fromLngLat(it.lng, it.lat) }.toMutableList()
                points.add(0, Point.fromLngLat(marker.lng, marker.lat)) // prepend current position for a continuous line

                val routeFeature = Feature.fromGeometry(LineString.fromLngLats(points)).apply {
                    addStringProperty("color", marker.markerColorHex)
                }
                familyRouteFeatures.add(routeFeature)

                prev.forEach { p ->
                    if (p.heading > 0.0) {
                        val arrowProps = JsonObject().apply {
                            addProperty("heading", p.heading)
                            addProperty("color", marker.markerColorHex)
                        }
                        arrowFeatures.add(Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat), arrowProps))
                    }
                }
            }
            if (marker.heading > 0.0) {
                val arrowProps = JsonObject().apply {
                    addProperty("heading", marker.heading)
                    addProperty("color", marker.markerColorHex)
                }
                arrowFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat), arrowProps))
            }
        }
    } else {
        familyRoutesSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        
        displayData.primaryMarker?.let { marker ->
            val props = JsonObject().apply {
                addProperty("deviceId", "self")
                addProperty("label", marker.letter)
                addProperty("color", marker.colorHex)
            }
            markerFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat), props))

            if (marker.emergencyState) {
                sosFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat)))
            }
        }

        if (displayData.routePoints.isEmpty()) {
            routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        } else {
            style.getLayerAs<LineLayer>(LAYER_ROUTE)?.setProperties(
                PropertyFactory.lineColor(localStyle.routeColorHex)
            )
            val points = displayData.routePoints.map { Point.fromLngLat(it.lng, it.lat) }
            val routeFeature = Feature.fromGeometry(LineString.fromLngLats(points))
            routeSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(routeFeature)))

            displayData.routePoints.forEach { p ->
                if (p.heading > 0.0) {
                    val arrowProps = JsonObject().apply {
                        addProperty("heading", p.heading)
                        addProperty("color", localStyle.routeColorHex)
                    }
                    arrowFeatures.add(Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat), arrowProps))
                }
                if (p.accuracy > 0.0) {
                    val accPolygon = createAccuracyPolygon(Point.fromLngLat(p.lng, p.lat), p.accuracy.toDouble())
                    val accProps = JsonObject().apply { addProperty("color", localStyle.routeColorHex) }
                    accuracyFeatures.add(Feature.fromGeometry(accPolygon, accProps))
                }
            }
        }
    }

    markerSource.setGeoJson(FeatureCollection.fromFeatures(markerFeatures.toTypedArray()))
    sosSource.setGeoJson(FeatureCollection.fromFeatures(sosFeatures.toTypedArray()))
    accuracySource.setGeoJson(FeatureCollection.fromFeatures(accuracyFeatures.toTypedArray()))
    familyRoutesSource.setGeoJson(FeatureCollection.fromFeatures(familyRouteFeatures.toTypedArray()))
    arrowsSource.setGeoJson(FeatureCollection.fromFeatures(arrowFeatures.toTypedArray()))
}

fun fitCameraToRoute(
    map: MapLibreMap,
    displayData: MapDisplayData,
) {
    if (displayData.isFamilyMode) {
        val markers = displayData.familyMarkers
        if (markers.isEmpty()) return
        if (markers.size == 1) {
            val only = markers.first()
            map.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(only.lat, only.lng))
                        .zoom(14.0)
                        .build()
                ),
                800
            )
            return
        }
        val boundsBuilder = LatLngBounds.Builder()
        markers.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
            1000
        )
    } else {
        val routeHistory = displayData.routePoints
        if (routeHistory.isEmpty()) return
        if (routeHistory.size == 1) {
            val only = routeHistory.first()
            map.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(only.lat, only.lng))
                        .zoom(15.0)
                        .build()
                ),
                800
            )
            return
        }
        val boundsBuilder = LatLngBounds.Builder()
        routeHistory.forEach { boundsBuilder.include(LatLng(it.lat, it.lng)) }
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80),
            1000
        )
    }
}
