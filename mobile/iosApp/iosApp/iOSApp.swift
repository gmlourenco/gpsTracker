import SwiftUI
import shared
import BackgroundTasks

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        // Init Koin
        let supabaseUrl = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String ?? ""
        let supabaseKey = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_KEY") as? String ?? ""
        let deviceApiSecret = Bundle.main.object(forInfoDictionaryKey: "DEVICE_API_SECRET") as? String ?? ""
        let backendBaseUrl = Bundle.main.object(forInfoDictionaryKey: "BACKEND_BASE_URL") as? String ?? ""
        
        KoinIOSKt.doInitKoin(
            supabaseUrl: supabaseUrl,
            supabaseKey: supabaseKey,
            deviceApiSecret: deviceApiSecret,
            backendBaseUrl: backendBaseUrl,
            isDebug: true
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
        
        let syncTask = Task { [weak task] in
            do {
                try await offlineManager.processQueue()
                task?.setTaskCompleted(success: true)
            } catch {
                print("Failed background sync: \(error)")
                task?.setTaskCompleted(success: false)
            }
        }
        
        task.expirationHandler = { [weak task] in
            #warning("CRITICAL: Swift Task cancellation does not propagate to Kotlin Coroutines. Integrate SKIE plugin in KMP shared module to prevent memory leaks here.")
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
