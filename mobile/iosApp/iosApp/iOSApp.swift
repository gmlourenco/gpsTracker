import SwiftUI
import shared
import BackgroundTasks
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
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
            guard let refreshTask = task as? BGAppRefreshTask else { return }
            self?.handleSyncTask(task: refreshTask)
        }
        
        SettingsSyncCoordinator.shared.start()
        
        // Push Notifications Registration
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            print("Push authorization granted: \(granted)")
            if let error = error {
                print("Push authorization error: \(error.localizedDescription)")
            }
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        
        return true
    }
    
    private func handleSyncTask(task: BGAppRefreshTask) {
        scheduleNextSync()
        
        let offlineManager = KoinIOSKt.getOfflineRequestManager()
        let syncEngine = KoinIOSKt.getSyncEngine()
        
        let syncTask = Task.detached(priority: .background) { [weak task] in
            do {
                try await offlineManager.processQueue()
                let syncResult = try await syncEngine.flush()
                print("SyncEngine flush complete. SOS: \(syncResult.emergencySynced), Latest: \(syncResult.latestSynced), History: \(syncResult.historySynced), Errors: \(syncResult.errors)")
                
                task?.setTaskCompleted(success: true)
            } catch is CancellationError {
                print("Background sync cancelled.")
                task?.setTaskCompleted(success: false)
            } catch {
                print("Failed background sync: \(error.localizedDescription)")
                task?.setTaskCompleted(success: false)
            }
        }
        
        task.expirationHandler = { [weak task] in
            print("Background task expiration handler called by system.")
            syncTask.cancel()
            task?.setTaskCompleted(success: false)
        }
    }
    
    func scheduleNextSync() {
        let request = BGAppRefreshTaskRequest(identifier: "com.segurancarural.gpstracker.sync")
        
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule sync: \(error)")
        }
    }
    
    // MARK: - Push Notifications
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let tokenParts = deviceToken.map { data in String(format: "%02.2hhx", data) }
        let token = tokenParts.joined()
        print("Device Token: \(token)")
        
        // Enqueue the token update via KMP's OfflineRequestManager
        let deviceSerialNumber = UIDevice.current.identifierForVendor?.uuidString ?? "ios-device"
        let bodyJson = "{\"serialNumber\":\"\(deviceSerialNumber)\",\"fcmToken\":\"\(token)\"}"
        
        KoinIOSKt.getOfflineRequestManager().enqueue(
            serviceType: "FCM_TOKEN",
            url: ApiRoutes().FCM_TOKEN,
            method: "PATCH",
            bodyJson: bodyJson
        )
    }
    
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Failed to register for remote notifications: \(error)")
    }
    
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable : Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        print("Received remote notification: \(userInfo)")
        
        // Handle SOS payload
        // Example payload: {"sos": "true", "title": "SOS Emergency!", "body": "Family member needs help!"}
        if let isSos = userInfo["sos"] as? String, isSos == "true" || (userInfo["sos"] as? Bool) == true {
            let title = userInfo["title"] as? String ?? "Alerta SOS!"
            let body = userInfo["body"] as? String ?? "Um membro da família ativou o SOS."
            
            triggerLocalAlert(title: title, body: body)
            completionHandler(.newData)
        } else {
            completionHandler(.noData)
        }
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // Show the alert even when the app is in the foreground
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }
    
    private func triggerLocalAlert(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .defaultCritical
        
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Error triggering local alert: \(error)")
            } else {
                print("Local alert triggered for SOS")
            }
        }
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @SwiftUI.Environment(\.scenePhase) var scenePhase
    
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
