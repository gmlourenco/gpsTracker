import Foundation
import shared
import Network

class SyncCoordinator {
    static let shared = SyncCoordinator()
    
    private var syncTask: Task<Void, Never>?
    private lazy var syncEngine = KoinIOSKt.getSyncEngine()
    private lazy var offlineManager = KoinIOSKt.getOfflineRequestManager()
    private let lock = NSLock()
    
    private var monitor: NWPathMonitor?
    private let queue = DispatchQueue(label: "SyncCoordinatorNetwork")
    private var isNetworkSatisfied = false
    
    private init() {}
    
    func start() {
        lock.lock()
        defer { lock.unlock() }
        
        guard syncTask == nil else { return }
        
        monitor = NWPathMonitor()
        monitor?.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }
            let isSatisfied = path.status == .satisfied
            if isSatisfied && !self.isNetworkSatisfied {
                // Network restored, flush queue immediately
                print("SyncCoordinator: Network restored. Flushing queue.")
                self.flushImmediately()
            }
            self.isNetworkSatisfied = isSatisfied
        }
        monitor?.start(queue: queue)
        
        syncTask = Task.detached(priority: .background) { [weak self] in
            while !Task.isCancelled {
                do {
                    if let engine = self?.syncEngine {
                        let syncResult = try await engine.flush()
                        print("SyncCoordinator: SyncEngine flush complete. Total synced: \(syncResult.totalSynced)")
                    }
                    try await Task.sleep(nanoseconds: 30_000_000_000)
                } catch is CancellationError {
                    print("SyncCoordinator: Sync task cancelled")
                    break
                } catch {
                    print("SyncCoordinator: Sync task error: \(error)")
                    // Sleep briefly before retrying in case of immediate failure loops
                    try? await Task.sleep(nanoseconds: 5_000_000_000)
                }
            }
        }
    }
    
    func stop() {
        lock.lock()
        defer { lock.unlock() }
        
        monitor?.cancel()
        monitor = nil
        syncTask?.cancel()
        syncTask = nil
    }
    
    private func flushImmediately() {
        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self = self else { return }
            do {
                try await self.offlineManager.processQueue()
                let result = try await self.syncEngine.flush()
                print("SyncCoordinator: Immediate flush complete. Total synced: \(result.totalSynced)")
            } catch {
                print("SyncCoordinator: Immediate flush failed: \(error)")
            }
        }
    }
}
