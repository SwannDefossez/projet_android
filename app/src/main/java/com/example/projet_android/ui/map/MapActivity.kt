package com.example.projet_android.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.projet_android.R
import com.example.projet_android.TourGuideApplication
import com.example.projet_android.domain.model.Poi
import com.example.projet_android.domain.usecase.GetRouteUseCase
import com.example.projet_android.domain.usecase.LoadPoisUseCase
import com.example.projet_android.ui.camera.CameraActivity
import com.example.projet_android.ui.navigation.NavigationExtras
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MapActivity : FragmentActivity(), OnMapReadyCallback, SensorEventListener {

    private val viewModel: MapViewModel by viewModels {
        val appContainer = (application as TourGuideApplication).appContainer
        MapViewModelFactory(
            loadPoisUseCase = LoadPoisUseCase(appContainer.poiRepository),
            getRouteUseCase = GetRouteUseCase(appContainer.routingRepository)
        )
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val sensorManager by lazy {
        getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private val rotationSensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private lateinit var selectedPoiText: TextView
    private lateinit var statusText: TextView
    private lateinit var recenterButton: Button
    private lateinit var openCameraButton: Button

    private lateinit var locationCallback: LocationCallback
    private lateinit var googleMap: GoogleMap

    private val poiMarkers = mutableMapOf<String, Marker>()
    private var routePolyline: Polyline? = null
    private var lastKnownLocation: Location? = null
    private var hasCenteredMap = false
    private var lastShownError: String? = null
    private var nearbyPoiForCamera: Poi? = null
    private var lastNearEnoughPoiId: String? = null
    private var lastBearingUpdateMs = 0L
    private var smoothedBearing: Float? = null
    private var lastAppliedBearing: Float? = null
    private var isUserGestureOnMap = false

    private val locationPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                enableLocationLayer()
                startLocationUpdates()
            } else {
                statusText.text = getString(R.string.map_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        selectedPoiText = findViewById(R.id.selectedPoiText)
        statusText = findViewById(R.id.statusText)
        recenterButton = findViewById(R.id.recenterButton)
        openCameraButton = findViewById(R.id.openCameraButton)

        recenterButton.setOnClickListener {
            val location = lastKnownLocation ?: return@setOnClickListener
            val latLng = LatLng(location.latitude, location.longitude)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
        openCameraButton.setOnClickListener {
            nearbyPoiForCamera?.let { poi ->
                launchCamera(poi)
            }
        }

        setupLocationCallback()
        observeUiState()
        attachMapFragment()
        viewModel.loadPois()
    }

    private fun attachMapFragment() {
        val existingFragment = supportFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG)
        val mapFragment = if (existingFragment is SupportMapFragment) {
            existingFragment
        } else {
            SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it, MAP_FRAGMENT_TAG)
                    .commit()
            }
        }
        mapFragment.getMapAsync(this)
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MapUiState) {
        selectedPoiText.text = state.selectedPoi?.let {
            getString(R.string.map_selected_poi, it.name)
        } ?: getString(R.string.map_select_poi_prompt)

        statusText.text = when {
            state.isLoadingPois -> getString(R.string.map_loading_poi)
            state.isLoadingRoute -> getString(R.string.map_loading_route)
            state.route != null -> getString(
                R.string.map_route_summary,
                (state.route.distanceMeters / 1000.0),
                (state.route.durationSeconds / 60.0)
            )
            else -> getString(R.string.map_ready)
        }

        updatePoiMarkers(state.pois, state.selectedPoi)
        updateRoutePolyline(state.route?.polylinePoints ?: emptyList())
        updateCameraButtonAvailability(lastKnownLocation)

        val error = state.errorMessage
        if (!error.isNullOrBlank() && error != lastShownError) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            lastShownError = error
        }
        if (error == null) {
            lastShownError = null
        }
    }

    private fun updatePoiMarkers(pois: List<Poi>, selectedPoi: Poi?) {
        if (!::googleMap.isInitialized) return

        val ids = pois.map { it.id }.toSet()
        val markerIdsToRemove = poiMarkers.keys.filterNot { it in ids }
        markerIdsToRemove.forEach { id ->
            poiMarkers.remove(id)?.remove()
        }

        pois.forEach { poi ->
            val marker = poiMarkers[poi.id]
            if (marker == null) {
                val created = googleMap.addMarker(
                    MarkerOptions()
                        .position(poi.toLatLng())
                        .title(poi.name)
                        .snippet(poi.description)
                        .zIndex(2f)
                )
                if (created != null) {
                    created.tag = poi
                    poiMarkers[poi.id] = created
                }
            } else {
                marker.position = poi.toLatLng()
                marker.title = poi.name
                marker.snippet = poi.description
                marker.tag = poi
            }
        }

        poiMarkers.forEach { (poiId, marker) ->
            marker.setIcon(
                if (selectedPoi?.id == poiId) {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                } else {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                }
            )
        }
    }

    private fun updateRoutePolyline(points: List<LatLng>) {
        if (!::googleMap.isInitialized) return
        routePolyline?.remove()
        routePolyline = if (points.isNotEmpty()) {
            googleMap.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(ContextCompat.getColor(this, R.color.route_blue))
                    .width(10f)
            )
        } else {
            null
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.apply {
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
            isZoomControlsEnabled = false
            isIndoorLevelPickerEnabled = false
        }
        googleMap.setOnCameraMoveStartedListener { reason ->
            isUserGestureOnMap = reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
        }
        googleMap.setOnCameraIdleListener {
            isUserGestureOnMap = false
        }
        googleMap.setOnMarkerClickListener { marker ->
            val poi = marker.tag as? Poi ?: return@setOnMarkerClickListener false
            viewModel.selectPoi(poi)
            Toast.makeText(
                this,
                getString(R.string.map_poi_selected_toast, poi.name),
                Toast.LENGTH_SHORT
            ).show()
            lastKnownLocation?.let { location ->
                viewModel.loadRoute(LatLng(location.latitude, location.longitude))
            }
            true
        }

        // If POI data arrived before map init, force a render now.
        renderState(viewModel.uiState.value)

        enableLocationLayer()
        requestLocationPermissionsIfNeeded()
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationLayer() {
        if (!::googleMap.isInitialized || !hasLocationPermission()) return
        googleMap.isMyLocationEnabled = true
    }

    private fun requestLocationPermissionsIfNeeded() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            locationPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                onLocationUpdated(location)
            }
        }
    }

    private fun onLocationUpdated(location: Location) {
        lastKnownLocation = location
        val position = LatLng(location.latitude, location.longitude)

        if (!::googleMap.isInitialized) return

        if (!hasCenteredMap) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
            hasCenteredMap = true
        }

        val state = viewModel.uiState.value
        if (state.selectedPoi != null && state.route == null && !state.isLoadingRoute) {
            viewModel.loadRoute(position)
        }
        updateCameraButtonAvailability(location)
    }

    private fun updateCameraButtonAvailability(location: Location?) {
        val selectedPoi = viewModel.uiState.value.selectedPoi
        if (location == null || selectedPoi == null) {
            nearbyPoiForCamera = null
            openCameraButton.visibility = View.GONE
            return
        }

        val distanceMeters = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            selectedPoi.latitude,
            selectedPoi.longitude,
            distanceMeters
        )

        val isNearEnough = distanceMeters[0] <= CAMERA_ENABLE_RADIUS_METERS
        nearbyPoiForCamera = selectedPoi.takeIf { isNearEnough }
        openCameraButton.visibility = if (isNearEnough) View.VISIBLE else View.GONE

        if (isNearEnough && lastNearEnoughPoiId != selectedPoi.id) {
            lastNearEnoughPoiId = selectedPoi.id
            Toast.makeText(
                this,
                getString(R.string.map_camera_available_toast, CAMERA_ENABLE_RADIUS_METERS.toInt()),
                Toast.LENGTH_SHORT
            ).show()
        }
        if (!isNearEnough && lastNearEnoughPoiId == selectedPoi.id) {
            lastNearEnoughPoiId = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L
        )
            .setMinUpdateIntervalMillis(2_500L)
            .build()
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun launchCamera(poi: Poi) {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(NavigationExtras.EXTRA_POI_ID, poi.id)
            putExtra(NavigationExtras.EXTRA_POI_NAME, poi.name)
            putExtra(NavigationExtras.EXTRA_POI_ADDRESS, poi.address)
            putExtra(NavigationExtras.EXTRA_POI_LAT, poi.latitude)
            putExtra(NavigationExtras.EXTRA_POI_LON, poi.longitude)
            putExtra(NavigationExtras.EXTRA_POI_DESC, poi.description)
            putExtra(NavigationExtras.EXTRA_POI_REF_IMAGE, poi.referenceImageAssetPath)
            putExtra(NavigationExtras.EXTRA_POI_AUDIO, poi.audioResName)
            putExtra(NavigationExtras.EXTRA_POI_THRESHOLD, poi.orbMatchThreshold)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        requestLocationPermissionsIfNeeded()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!::googleMap.isInitialized || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (isUserGestureOnMap) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBearingUpdateMs < BEARING_UPDATE_INTERVAL_MS) return
        lastBearingUpdateMs = now

        val rotationMatrix = FloatArray(9)
        val adjustedRotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_0 -> {
                rotationMatrix.copyInto(adjustedRotationMatrix)
            }

            Surface.ROTATION_90 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    adjustedRotationMatrix
                )
            }

            Surface.ROTATION_180 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    adjustedRotationMatrix
                )
            }

            Surface.ROTATION_270 -> {
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    adjustedRotationMatrix
                )
            }
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjustedRotationMatrix, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val targetBearing = normalizeBearing(azimuth)

        val previousSmoothed = smoothedBearing ?: targetBearing
        val smoothed = normalizeBearing(
            previousSmoothed + shortestDeltaDegrees(previousSmoothed, targetBearing) * BEARING_SMOOTHING_ALPHA
        )
        smoothedBearing = smoothed

        val lastApplied = lastAppliedBearing
        if (lastApplied != null) {
            val delta = kotlin.math.abs(shortestDeltaDegrees(lastApplied, smoothed))
            if (delta < MIN_BEARING_DELTA_DEGREES) return
        }
        lastAppliedBearing = smoothed

        val updatedPosition = CameraPosition.Builder(googleMap.cameraPosition)
            .bearing(smoothed)
            .build()
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(updatedPosition))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun normalizeBearing(value: Float): Float {
        return (value % 360f + 360f) % 360f
    }

    private fun shortestDeltaDegrees(from: Float, to: Float): Float {
        return ((to - from + 540f) % 360f) - 180f
    }

    companion object {
        private const val MAP_FRAGMENT_TAG = "map_fragment"
        private const val CAMERA_ENABLE_RADIUS_METERS = 50f
        private const val BEARING_UPDATE_INTERVAL_MS = 5_000L
        private const val BEARING_SMOOTHING_ALPHA = 0.22f
        private const val MIN_BEARING_DELTA_DEGREES = 1.5f
    }
}
