import Foundation
import CoreMotion
import shared

class AccidentDetector {
    static let shared = AccidentDetector()
    
    private let motionManager = CMMotionManager()
    private let motionQueue = OperationQueue()
    
    private var isListening = false
    private var lastTriggerTime: TimeInterval = 0
    private let debounceMs: TimeInterval = 5.0 // 5 seconds
    private let postImpactAnalysisMs = 2.0 // 2 seconds
    
    private var consecutiveOverThresholdCount = 0
    private let requiredConsecutiveSamples = 3
    
    private var threshold: Double = 7.5 // default 7.5G
    private let rolloverThresholdRadians: Double = 0.785398 // 45 degrees
    
    private var isCrashDetected = false
    
    // Speed threshold state
    private var lastLowSpeedTime: TimeInterval?
    private let speedThresholdMps: Double = 0.55 // approx 2.0 km/h
    private let speedDelaySecs: TimeInterval = 30.0
    private var isPausedDueToLowSpeed = false
    
    private init() {
        motionQueue.name = "AccidentDetectorQueue"
        motionQueue.maxConcurrentOperationCount = 1
        motionQueue.qualityOfService = .background
    }
    
    func start() {
        let sensitivity = UserDefaults.standard.string(forKey: "accident_sensor_sensitivity") ?? "medium"
        
        motionQueue.addOperation { [weak self] in
            guard let self = self else { return }
            
            if sensitivity.lowercased() == "off" {
                print("AccidentDetector: Sensor explicitly disabled in settings.")
                return
            }
            
            if sensitivity.lowercased().hasPrefix("custom_") {
                let gStr = sensitivity.dropFirst("custom_".count)
                self.threshold = Double(gStr) ?? 7.0
            } else if sensitivity.lowercased() == "alta" {
                self.threshold = 5.0
            } else if sensitivity.lowercased() == "baixa" {
                self.threshold = 10.0
            } else {
                self.threshold = 7.5
            }
            
            guard self.motionManager.isDeviceMotionAvailable else {
                print("AccidentDetector: Device motion not available")
                return
            }
            
            if !self.isListening {
                self.isCrashDetected = false
                self.isListening = true
                self.consecutiveOverThresholdCount = 0
                self.isPausedDueToLowSpeed = false
                self.lastLowSpeedTime = nil
                
                self.resumeUpdates()
                
                print("AccidentDetector: Started with sensitivity: \(sensitivity) (threshold: \(self.threshold)G)")
            }
        }
    }
    
    func stop() {
        motionQueue.addOperation { [weak self] in
            guard let self = self else { return }
            if self.isListening {
                self.motionManager.stopDeviceMotionUpdates()
                self.isListening = false
                self.consecutiveOverThresholdCount = 0
                self.isPausedDueToLowSpeed = false
                print("AccidentDetector: Stopped")
            }
        }
    }
    
    private func resumeUpdates() {
        guard isListening, !isPausedDueToLowSpeed else { return }
        motionManager.deviceMotionUpdateInterval = 0.1 // 10 Hz for background battery optimization
        
        motionManager.startDeviceMotionUpdates(using: .xTrueNorthZVertical, to: motionQueue) { [weak self] (motion, error) in
            guard let self = self, let motion = motion, error == nil else { return }
            self.processMotion(motion)
        }
    }
    
    private func pauseUpdates() {
        motionManager.stopDeviceMotionUpdates()
        print("AccidentDetector: Paused motion updates due to low speed")
    }
    
    func updateSpeed(_ speed: Double) {
        motionQueue.addOperation { [weak self] in
            guard let self = self else { return }
            
            if speed < self.speedThresholdMps {
                if self.lastLowSpeedTime == nil {
                    self.lastLowSpeedTime = Date().timeIntervalSince1970
                } else if let lastTime = self.lastLowSpeedTime, (Date().timeIntervalSince1970 - lastTime) >= self.speedDelaySecs {
                    if !self.isPausedDueToLowSpeed {
                        self.isPausedDueToLowSpeed = true
                        self.pauseUpdates()
                    }
                }
            } else {
                self.lastLowSpeedTime = nil
                if self.isPausedDueToLowSpeed {
                    self.isPausedDueToLowSpeed = false
                    print("AccidentDetector: Resuming motion updates due to increased speed")
                    self.resumeUpdates()
                }
            }
        }
    }
    
    private func processMotion(_ motion: CMDeviceMotion) {
        if isCrashDetected { return }
        
        // CMDeviceMotion natively isolates user acceleration from gravity
        let accX = motion.userAcceleration.x
        let accY = motion.userAcceleration.y
        let accZ = motion.userAcceleration.z
        
        // Calculate magnitude in Gs
        let magnitude = sqrt(accX * accX + accY * accY + accZ * accZ)
        
        if magnitude > threshold {
            consecutiveOverThresholdCount += 1
            if consecutiveOverThresholdCount >= requiredConsecutiveSamples {
                let now = Date().timeIntervalSince1970
                if now - lastTriggerTime > debounceMs {
                    lastTriggerTime = now
                    print("🚨 CRITICAL IMPACT DETECTED! Magnitude: \(magnitude)G")
                    consecutiveOverThresholdCount = 0
                    
                    onImpactConfirmed()
                }
            }
        } else {
            consecutiveOverThresholdCount = 0
        }
    }
    
    private func onImpactConfirmed() {
        DispatchQueue.main.asyncAfter(deadline: .now() + postImpactAnalysisMs) { [weak self] in
            guard let self = self else { return }
            
            var isRollover = false
            if let motion = self.motionManager.deviceMotion {
                let roll = abs(motion.attitude.roll)
                let pitch = abs(motion.attitude.pitch)
                if roll > self.rolloverThresholdRadians || pitch > self.rolloverThresholdRadians {
                    isRollover = true
                    print("AccidentDetector: Post-impact analysis: Rollover detected! (Roll: \(roll), Pitch: \(pitch))")
                }
            }
            
            self.motionQueue.addOperation { [weak self] in
                self?.triggerPreSos()
            }
        }
    }
    
    private func triggerPreSos() {
        isCrashDetected = true
        TrackingStateRepository.shared.setPreSosActive(active: true)
    }
}
