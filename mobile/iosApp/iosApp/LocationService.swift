import Foundation
import CoreLocation
import shared
import UIKit
import Network

class LocationService: NSObject, CLLocationManagerDelegate, ObservableObject {
    static let shared = LocationService()
    
    private let locationManager = CLLocationManager()
    private let batteryThreshold: Float = 0.20 // 20%
    
    // Cached dependencies and unchanging properties to reduce bridging overhead
    private lazy var telemetryRepository = KoinIOSKt.getTelemetryRepository()
    private let deviceSerialNumber: String = UIDevice.current.identifierForVendor?.uuidString ?? "ios-device"
    private let deviceName: String = UIDevice.current.name
    private let appVersion: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
    private let dateFormatter = ISO8601DateFormatter()
    
    @Published var isTracking = false
    @Published var isHighAccuracyMode = false
    
    private var isSosActive = false
    private var isSosObserver: shared.Closeable?
    
    private let monitor = NWPathMonitor()
    private var networkType: String = "UNKNOWN"
    
    private override init() {
        super.init()
        locationManager.delegate = self
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = true // Allow iOS to pause if stationary
        
        // Enable battery monitoring to adapt accuracy
        UIDevice.current.isBatteryMonitoringEnabled = true
        
        NotificationCenter.default.addObserver(self, selector: #selector(batteryLevelDidChange), name: UIDevice.batteryLevelDidChangeNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(batteryStateDidChange), name: UIDevice.batteryStateDidChangeNotification, object: nil)
        
        monitor.pathUpdateHandler = { [weak self] path in
            if path.status == .satisfied {
                if path.usesInterfaceType(.wifi) {
                    self?.networkType = "WIFI"
                } else if path.usesInterfaceType(.cellular) {
                    self?.networkType = "CELLULAR"
                } else {
                    self?.networkType = "OTHER"
                }
            } else {
                self?.networkType = "NONE"
            }
        }
        let queue = DispatchQueue(label: "NetworkMonitor")
        monitor.start(queue: queue)
        
        isSosObserver = TrackingStateRepository.shared.observeIsSosActive().watch { [weak self] active in
            self?.isSosActive = active?.boolValue ?? false
            if self?.isSosActive == true {
                self?.updateTrackingMode()
            }
        }
    }
    
    func startTracking() {
        locationManager.requestAlwaysAuthorization()
        isTracking = true
        updateTrackingMode()
        AccidentDetector.shared.start()
    }
    
    func stopTracking() {
        locationManager.stopUpdatingLocation()
        locationManager.stopMonitoringSignificantLocationChanges()
        isTracking = false
        AccidentDetector.shared.stop()
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
        
        let shouldBeHighAccuracy = isCharging || (batteryLevel > batteryThreshold) || (batteryLevel < 0) || isSosActive // <0 means unknown/simulator
        
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
        
        // Filter out highly inaccurate locations (equivalent to Android)
        if location.horizontalAccuracy > 250 && !self.isSosActive {
            print("LocationService: Ignored low accuracy location (\(location.horizontalAccuracy)m)")
            return
        }
        
        let batteryLevel = UIDevice.current.batteryLevel
        let isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        
        let timestampStr = dateFormatter.string(from: location.timestamp)
        
        // Break up expressions to help Swift type checker
        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude
        let accuracy = Float(location.horizontalAccuracy)
        let speed = Float(max(0, location.speed))
        let heading = Float(max(0, location.course))
        let batteryLv = batteryLevel < 0 ? Int32(100) : Int32(batteryLevel * 100)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        
        // Create TelemetryRecord using the correct KMP generated init
        // Uses cached device information properties to avoid repeated ObjC bridging overhead
        let record = TelemetryRecord(
            id: 0,
            serialNumber: deviceSerialNumber,
            deviceLabel: deviceName,
            timestamp: timestampStr,
            batteryLevel: batteryLv,
            batteryCharging: isCharging,
            lat: lat,
            lng: lng,
            accuracy: accuracy,
            speed: speed,
            heading: heading,
            emergencyState: self.isSosActive,
            trackingEnabled: true,
            networkType: self.networkType,
            appVersion: appVersion,
            createdAtEpochMs: nowMs,
            syncState: 0
        )
        
        // Fetch repo outside the detached task (on main thread) to ensure lazy var thread safety
        let repo = self.telemetryRepository
        
        // Use detached Task on background priority to avoid blocking UI MainActor thread
        Task.detached(priority: .background) {
            do {
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
