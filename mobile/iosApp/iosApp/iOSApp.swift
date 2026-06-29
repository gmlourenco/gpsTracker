import SwiftUI
import shared
import BackgroundTasks

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        // Init Koin
        // TODO: Em produção, ler do Info.plist ou xcconfig (Environment)
        KoinIOSKt.initKoin(
            supabaseUrl: "https://SUPABASE_URL_AQUI",
            supabaseKey: "SUPABASE_KEY_AQUI",
            deviceApiSecret: "SECRET",
            backendBaseUrl: "https://BACKEND_URL_AQUI",
            isDebug: true
        )
        
        // Register Background Task
        BGTaskScheduler.shared.register(forTaskWithIdentifier: "com.segurancarural.gpstracker.sync", using: nil) { task in
            self.handleSyncTask(task: task as! BGProcessingTask)
        }
        
        return true
    }
    
    private func handleSyncTask(task: BGProcessingTask) {
        scheduleNextSync()
        
        let offlineManager = KoinIOSKt.getOfflineRequestManager()
        
        let syncTask = Task {
            do {
                try await offlineManager.processQueue()
                task.setTaskCompleted(success: true)
            } catch {
                print("Failed background sync: \(error)")
                task.setTaskCompleted(success: false)
            }
        }
        
        task.expirationHandler = {
            syncTask.cancel()
            task.setTaskCompleted(success: false)
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
