import Foundation
import shared
import UIKit

class SettingsSyncCoordinator: ObservableObject {
    static let shared = SettingsSyncCoordinator()
    
    @Published var trackingDistanceM: Double
    @Published var trackingIntervalMs: Int
    @Published var accidentSensorSensitivity: String
    
    private var syncTask: Task<Void, Never>?
    private let lock = NSLock()
    
    private let deviceSerialNumber = UIDevice.current.identifierForVendor?.uuidString ?? "ios-device"
    
    private init() {
        self.trackingDistanceM = UserDefaults.standard.double(forKey: "tracking_distance_m")
        self.trackingIntervalMs = UserDefaults.standard.integer(forKey: "tracking_interval_ms")
        self.accidentSensorSensitivity = UserDefaults.standard.string(forKey: "accident_sensor_sensitivity") ?? "medium"
        
        if self.trackingDistanceM <= 0 { self.trackingDistanceM = 10.0 }
        if self.trackingIntervalMs <= 0 { self.trackingIntervalMs = 5000 }
    }
    
    func start() {
        lock.lock()
        defer { lock.unlock() }
        
        guard syncTask == nil else { return }
        
        syncTask = Task.detached(priority: .background) { [weak self] in
            guard let self = self else { return }
            while !Task.isCancelled {
                do {
                    let response = try await KoinIOSKt.fetchDeviceConfig(serialNumber: self.deviceSerialNumber)
                    if let config = response.config {
                        DispatchQueue.main.async {
                            self.trackingDistanceM = Double(config.trackingDistanceM)
                            self.trackingIntervalMs = Int(config.trackingIntervalMs)
                            if !config.accidentSensorSensitivity.isEmpty {
                                self.accidentSensorSensitivity = config.accidentSensorSensitivity
                            }
                        }
                        
                        UserDefaults.standard.set(config.trackingDistanceM, forKey: "tracking_distance_m")
                        UserDefaults.standard.set(config.trackingIntervalMs, forKey: "tracking_interval_ms")
                        
                        if !config.accidentSensorSensitivity.isEmpty {
                            UserDefaults.standard.set(config.accidentSensorSensitivity, forKey: "accident_sensor_sensitivity")
                        }
                        print("SettingsSyncCoordinator: Synced settings from KMP successfully.")
                    }
                    // Fetch every 15 minutes (900 seconds)
                    try await Task.sleep(nanoseconds: 900_000_000_000)
                } catch is CancellationError {
                    print("SettingsSyncCoordinator: Sync task cancelled")
                    break
                } catch {
                    print("SettingsSyncCoordinator: Sync task error: \(error)")
                    // Sleep for 1 minute before retrying
                    try? await Task.sleep(nanoseconds: 60_000_000_000)
                }
            }
        }
    }
    
    func stop() {
        lock.lock()
        defer { lock.unlock() }
        
        syncTask?.cancel()
        syncTask = nil
    }
}
