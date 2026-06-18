package com.segurancarural.gpstracker.util

import org.maplibre.android.style.expressions.Expression
import com.google.gson.JsonObject
import com.segurancarural.gpstracker.ui.model.DeviceMapStyle
import com.segurancarural.gpstracker.ui.model.MapDisplayData
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

const val SOURCE_ROUTE = "route-source"
const val LAYER_ROUTE = "route-layer"
const val SOURCE_MARKER = "marker-source"
const val LAYER_MARKER_CIRCLE = "marker-circle-layer"
const val LAYER_MARKER_LABEL = "marker-label-layer"
const val SOURCE_SOS = "sos-source"
const val LAYER_SOS = "sos-layer"
const val SosRedHex = "#DC2626"

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

    // 1. Route Line Layer (personal route mode only)
    if (displayData.isFamilyMode || displayData.routePoints.isEmpty()) {
        routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    } else {
        style.getLayerAs<LineLayer>(LAYER_ROUTE)?.setProperties(
            PropertyFactory.lineColor(localStyle.routeColorHex)
        )
        val points = displayData.routePoints.map { Point.fromLngLat(it.lng, it.lat) }
        val routeFeature = Feature.fromGeometry(LineString.fromLngLats(points))
        routeSource.setGeoJson(FeatureCollection.fromFeatures(arrayOf(routeFeature)))
    }

    // 2. Markers & Emergency SOS States
    val markerFeatures = mutableListOf<Feature>()
    val sosFeatures = mutableListOf<Feature>()

    if (displayData.isFamilyMode) {
        displayData.familyMarkers.forEach { marker ->
            val props = JsonObject().apply {
                addProperty("deviceId", marker.deviceId)
                addProperty("label", marker.markerLetter)
                addProperty("color", marker.markerColorHex)
            }
            markerFeatures.add(
                Feature.fromGeometry(
                    Point.fromLngLat(marker.lng, marker.lat),
                    props
                )
            )

            if (marker.emergencyState) {
                sosFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat)))
            }
        }
    } else {
        displayData.primaryMarker?.let { marker ->
            val props = JsonObject().apply {
                addProperty("deviceId", "self")
                addProperty("label", marker.letter)
                addProperty("color", marker.colorHex)
            }
            markerFeatures.add(
                Feature.fromGeometry(
                    Point.fromLngLat(marker.lng, marker.lat),
                    props
                )
            )

            if (marker.emergencyState) {
                sosFeatures.add(Feature.fromGeometry(Point.fromLngLat(marker.lng, marker.lat)))
            }
        }
    }

    markerSource.setGeoJson(FeatureCollection.fromFeatures(markerFeatures.toTypedArray()))
    sosSource.setGeoJson(FeatureCollection.fromFeatures(sosFeatures.toTypedArray()))
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
