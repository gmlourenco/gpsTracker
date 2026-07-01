import Foundation
import CoreMotion
import shared

class AccidentDetector {
    static let shared = AccidentDetector()
    
    private let motionManager = CMMotionManager()
    private let motionQueue = OperationQueue()
    
    private let crashThresholdG: Double = 7.5
    private let rolloverThresholdRadians: Double = 0.785398 // 45 degrees
    
    private var isCrashDetected = false
    
    private init() {
        motionQueue.name = "AccidentDetectorQueue"
        motionQueue.maxConcurrentOperationCount = 1
    }
    
    func start() {
        guard motionManager.isDeviceMotionAvailable else {
            print("AccidentDetector: Device motion not available")
            return
        }
        
        isCrashDetected = false
        motionManager.deviceMotionUpdateInterval = 1.0 / 50.0 // 50 Hz
        motionManager.startDeviceMotionUpdates(to: motionQueue) { [weak self] (motion, error) in
            guard let self = self, let motion = motion, error == nil else { return }
            self.processMotion(motion)
        }
        print("AccidentDetector: Started")
    }
    
    func stop() {
        motionManager.stopDeviceMotionUpdates()
        print("AccidentDetector: Stopped")
    }
    
    private func processMotion(_ motion: CMDeviceMotion) {
        if isCrashDetected { return }
        
        // CMDeviceMotion natively isolates user acceleration from gravity
        let accX = motion.userAcceleration.x
        let accY = motion.userAcceleration.y
        let accZ = motion.userAcceleration.z
        
        // Calculate magnitude in Gs
        let magnitude = sqrt(accX * accX + accY * accY + accZ * accZ)
        
        if magnitude > crashThresholdG {
            // Check for rollover using attitude (roll or pitch > 45 deg)
            let roll = abs(motion.attitude.roll)
            let pitch = abs(motion.attitude.pitch)
            
            if roll > rolloverThresholdRadians || pitch > rolloverThresholdRadians {
                print("AccidentDetector: Crash & Rollover Detected! Magnitude: \(magnitude)G, Roll: \(roll), Pitch: \(pitch)")
                triggerPreSos()
            }
        }
    }
    
    private func triggerPreSos() {
        isCrashDetected = true
        TrackingStateRepository.shared.setPreSosActive(active: true)
    }
}
