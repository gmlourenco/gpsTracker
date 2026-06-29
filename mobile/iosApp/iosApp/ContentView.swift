import SwiftUI
import shared

struct ContentView: View {
    init() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 26/255, green: 26/255, blue: 46/255, alpha: 1.0) // SurfaceDark (#1A1A2E)
        
        UITabBar.appearance().standardAppearance = appearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = appearance
        }
    }
    
    var body: some View {
        TabView {
            HomeView()
                .tabItem {
                    Label("Início", systemImage: "house")
                }
            
            MapView()
                .tabItem {
                    Label("Mapa", systemImage: "map")
                }
            
            FamilyView()
                .tabItem {
                    Label("Família", systemImage: "person.3")
                }
            
            SettingsView()
                .tabItem {
                    Label("Config", systemImage: "gearshape")
                }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
