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
    private var isSosObserver: Task<Void, Never>?
    private var isTrackingObserver: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?
    
    deinit {
        isSosObserver?.cancel()
        isTrackingObserver?.cancel()
        heartbeatTask?.cancel()
    }
    
    private let monitor = NWPathMonitor()
    private var networkType: String = "UNKNOWN"
    private var lastLocationTimestamp: Date?
    
    private override init() {
        super.init()
        locationManager.delegate = self
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = false // Prevent iOS from pausing if stationary
        
        // Enable battery monitoring to adapt accuracy
        UIDevice.current.isBatteryMonitoringEnabled = true
        
        NotificationCenter.default.addObserver(self, selector: #selector(batteryLevelDidChange), name: UIDevice.batteryLevelDidChangeNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(batteryStateDidChange), name: UIDevice.batteryStateDidChangeNotification, object: nil)
        
        NotificationCenter.default.addObserver(self, selector: #selector(settingsChanged), name: UserDefaults.didChangeNotification, object: nil)
        
        monitor.pathUpdateHandler = { [weak self] path in
            let newType: String
            if path.status == .satisfied {
                if path.usesInterfaceType(.wifi) {
                    newType = "WIFI"
                } else if path.usesInterfaceType(.cellular) {
                    newType = "CELLULAR"
                } else {
                    newType = "OTHER"
                }
            } else {
                newType = "NONE"
            }
            DispatchQueue.main.async {
                self?.networkType = newType
            }
        }
        let queue = DispatchQueue(label: "NetworkMonitor")
        monitor.start(queue: queue)
        
        let sosFlow = TrackingStateRepository.shared.observeIsSosActive()
        isSosObserver = Task { [weak self] in
            for await active in streamCFlow(sosFlow) {
                let isActive: Bool
                if let kotlinBool = active as? KotlinBoolean {
                    isActive = kotlinBool.boolValue
                } else if let num = active as? NSNumber {
                    isActive = num.boolValue
                } else if let val = active as? Bool {
                    isActive = val
                } else {
                    continue
                }
                
                await MainActor.run {
                    self?.isSosActive = isActive
                    self?.updateTrackingMode()
                }
            }
        }
        
        let trackingFlow = TrackingStateRepository.shared.observeIsTracking()
        isTrackingObserver = Task { [weak self] in
            for await active in streamCFlow(trackingFlow) {
                let isActive: Bool
                if let kotlinBool = active as? KotlinBoolean {
                    isActive = kotlinBool.boolValue
                } else if let num = active as? NSNumber {
                    isActive = num.boolValue
                } else if let val = active as? Bool {
                    isActive = val
                } else {
                    continue
                }
                
                await MainActor.run {
                    self?.isTracking = isActive
                    if isActive {
                        self?.AccidentDetectorStart()
                        SyncCoordinator.shared.start()
                    } else {
                        self?.AccidentDetectorStop()
                        SyncCoordinator.shared.stop()
                    }
                    self?.updateTrackingMode()
                }
            }
        }
    }
    
    private func AccidentDetectorStart() {
        AccidentDetector.shared.start()
    }
    
    private func AccidentDetectorStop() {
        AccidentDetector.shared.stop()
    }
    
    func startTracking() {
        locationManager.requestAlwaysAuthorization()
        TrackingStateRepository.shared.setTracking(active: true)
        updateTrackingMode()
        
        heartbeatTask?.cancel()
        heartbeatTask = Task.detached(priority: .background) { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30 * 60 * 1_000_000_000) // 30 mins
                if Task.isCancelled { break }
                await MainActor.run {
                    self?.submitHeartbeatIfNecessary()
                }
            }
        }
    }
    
    private func submitHeartbeatIfNecessary() {
        guard isTracking, let location = locationManager.location else { return }
        
        let thirtyMinsAgo = Date().addingTimeInterval(-30 * 60)
        if let lastTime = lastLocationTimestamp, lastTime > thirtyMinsAgo {
            return
        }
        
        print("LocationService: Triggering 30-min fallback heartbeat")
        locationManager(locationManager, didUpdateLocations: [location])
    }
    
    func stopTracking() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
        locationManager.stopUpdatingLocation()
        locationManager.stopMonitoringSignificantLocationChanges()
        TrackingStateRepository.shared.setTracking(active: false)
    }
    
    @objc private func settingsChanged() {
        if isTracking { updateTrackingMode() }
    }
    
    @objc private func batteryLevelDidChange() {
        if isTracking { updateTrackingMode() }
    }
    
    @objc private func batteryStateDidChange() {
        if isTracking { updateTrackingMode() }
    }
    
    private func updateTrackingMode() {
        guard isTracking || isSosActive else {
            locationManager.stopUpdatingLocation()
            locationManager.stopMonitoringSignificantLocationChanges()
            isHighAccuracyMode = false
            return
        }
        
        let batteryLevel = UIDevice.current.batteryLevel
        let isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        
        let shouldBeHighAccuracy = isCharging || (batteryLevel > batteryThreshold) || (batteryLevel < 0) || isSosActive // <0 means unknown/simulator
        
        if shouldBeHighAccuracy {
            let dynamicDistance = SettingsSyncCoordinator.shared.trackingDistanceM
            let desiredDistance = isSosActive ? kCLDistanceFilterNone : (dynamicDistance > 0 ? dynamicDistance : 10.0)
            
            if !isHighAccuracyMode || locationManager.distanceFilter != desiredDistance {
                locationManager.stopMonitoringSignificantLocationChanges()
                locationManager.desiredAccuracy = kCLLocationAccuracyBest
                locationManager.distanceFilter = desiredDistance
                locationManager.startUpdatingLocation()
                isHighAccuracyMode = true
                print("LocationService: Switched to High Accuracy Mode (Distance Filter: \(locationManager.distanceFilter)m)")
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
        
        // Enforce tracking interval
        let dynamicIntervalMs = SettingsSyncCoordinator.shared.trackingIntervalMs
        let desiredIntervalMs = isSosActive ? 0 : (dynamicIntervalMs > 0 ? dynamicIntervalMs : 5000)
        
        if let lastTime = lastLocationTimestamp, Date().timeIntervalSince(lastTime) * 1000 < Double(desiredIntervalMs) {
            return
        }
        lastLocationTimestamp = Date()
        
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
        
        AccidentDetector.shared.updateSpeed(Double(speed))
        
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
            trackingEnabled: self.isTracking,
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
