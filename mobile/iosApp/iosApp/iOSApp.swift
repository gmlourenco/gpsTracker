import SwiftUI
import shared
import BackgroundTasks

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        // Init Koin
        let supabaseUrl = Environment.supabaseUrl
        let supabaseKey = Environment.supabaseKey
        let deviceApiSecret = Environment.deviceApiSecret
        let backendBaseUrl = Environment.backendBaseUrl
        let isDebug = Environment.isDebug
        
        print("DEBUG KMP SECRETS:")
        print("SUPABASE_URL: \(supabaseUrl)")
        print("BACKEND_BASE_URL: \(backendBaseUrl)")
        print("DEVICE_API_SECRET length: \(deviceApiSecret.count)")
        
        KoinIOSKt.doInitKoin(
            supabaseUrl: supabaseUrl,
            supabaseKey: supabaseKey,
            deviceApiSecret: deviceApiSecret,
            backendBaseUrl: backendBaseUrl,
            isDebug: isDebug
        )
        
        // Register Background Task
        BGTaskScheduler.shared.register(forTaskWithIdentifier: "com.segurancarural.gpstracker.sync", using: nil) { [weak self] task in
            guard let processingTask = task as? BGProcessingTask else { return }
            self?.handleSyncTask(task: processingTask)
        }
        
        return true
    }
    
    private func handleSyncTask(task: BGProcessingTask) {
        scheduleNextSync()
        
        let offlineManager = KoinIOSKt.getOfflineRequestManager()
        let syncEngine = KoinIOSKt.getSyncEngine()
        
        let syncTask = Task { [weak task] in
            do {
                try await offlineManager.processQueue()
                let syncResult = try await syncEngine.flush()
                print("SyncEngine flush complete. SOS: \(syncResult.emergencySynced), Latest: \(syncResult.latestSynced), History: \(syncResult.historySynced), Errors: \(syncResult.errors)")
                task?.setTaskCompleted(success: true)
            } catch {
                print("Failed background sync: \(error)")
                task?.setTaskCompleted(success: false)
            }
        }
        
        task.expirationHandler = { [weak task] in
            syncTask.cancel()
            task?.setTaskCompleted(success: false)
        }
    }
    
    func scheduleNextSync() {
        let request = BGProcessingTaskRequest(identifier: "com.segurancarural.gpstracker.sync")
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule sync: \(error)")
        }
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) var scenePhase
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .background {
                appDelegate.scheduleNextSync()
            }
        }
    }
}
