import Foundation
import CoreLocation
import shared
import UIKit

class LocationService: NSObject, CLLocationManagerDelegate, ObservableObject {
    static let shared = LocationService()
    
    private let locationManager = CLLocationManager()
    private let batteryThreshold: Float = 0.20 // 20%
    
    @Published var isTracking = false
    @Published var isHighAccuracyMode = false
    
    private override init() {
        super.init()
        locationManager.delegate = self
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = true // Allow iOS to pause if stationary
        
        // Enable battery monitoring to adapt accuracy
        UIDevice.current.isBatteryMonitoringEnabled = true
        
        NotificationCenter.default.addObserver(self, selector: #selector(batteryLevelDidChange), name: UIDevice.batteryLevelDidChangeNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(batteryStateDidChange), name: UIDevice.batteryStateDidChangeNotification, object: nil)
    }
    
    func startTracking() {
        locationManager.requestAlwaysAuthorization()
        isTracking = true
        updateTrackingMode()
    }
    
    func stopTracking() {
        locationManager.stopUpdatingLocation()
        locationManager.stopMonitoringSignificantLocationChanges()
        isTracking = false
    }
    
    @objc private func batteryLevelDidChange() {
        if isTracking { updateTrackingMode() }
    }
    
    @objc private func batteryStateDidChange() {
        if isTracking { updateTrackingMode() }
    }
    
    private func updateTrackingMode() {
        let batteryLevel = UIDevice.current.batteryLevel
        let isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        
        let shouldBeHighAccuracy = isCharging || (batteryLevel > batteryThreshold) || (batteryLevel < 0) // <0 means unknown/simulator
        
        if shouldBeHighAccuracy {
            if !isHighAccuracyMode {
                locationManager.stopMonitoringSignificantLocationChanges()
                locationManager.desiredAccuracy = kCLLocationAccuracyBest
                locationManager.distanceFilter = 10 // Update every 10 meters
                locationManager.startUpdatingLocation()
                isHighAccuracyMode = true
                print("LocationService: Switched to High Accuracy Mode")
            }
        } else {
            if isHighAccuracyMode || (!isHighAccuracyMode && isTracking) {
                locationManager.stopUpdatingLocation()
                locationManager.startMonitoringSignificantLocationChanges()
                isHighAccuracyMode = false
                print("LocationService: Switched to Significant Changes Mode (Battery Low)")
            }
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        let batteryLevel = UIDevice.current.batteryLevel
        let isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        
        // Create TelemetryRecord
        let record = TelemetryRecord(
            id: UUID().uuidString,
            timestamp: Int64(location.timestamp.timeIntervalSince1970 * 1000),
            lat: location.coordinate.latitude,
            lng: location.coordinate.longitude,
            altitude: location.altitude,
            accuracy: Float(location.horizontalAccuracy),
            speed: Float(max(0, location.speed)),
            heading: Float(max(0, location.course)),
            batteryLevel: batteryLevel < 0 ? 100 : Int32(batteryLevel * 100),
            batteryCharging: isCharging,
            emergencyState: 0, // 0 = Normal
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown",
            syncState: 0
        )
        
        Task {
            do {
                let repo = KoinIOSKt.getTelemetryRepository()
                try await repo.submitLocation(record: record)
                print("LocationService: Successfully passed location to KMP TelemetryRepository")
            } catch {
                print("LocationService: Failed to submit location to KMP - \(error)")
            }
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("LocationService: Location error: \(error)")
    }
}
