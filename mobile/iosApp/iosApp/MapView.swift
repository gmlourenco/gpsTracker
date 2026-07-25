import SwiftUI
import MapKit
import shared

struct MapView: View {
    @StateObject private var viewModel = MapViewModel()
    
    @AppStorage("default_map_type") private var defaultMapType: String = "SATELLITE"
    @State private var showFamilySheet = false
    
    private var nativeMapType: MKMapType {
        switch defaultMapType.uppercased() {
        case "NORMAL": return .standard
        case "TERRAIN": return .mutedStandard
        case "SATELLITE": return .hybrid
        default: return .hybrid
        }
    }
    
    // Default region (fallback)
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 39.3999, longitude: -8.2245), // Portugal center
        span: MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)
    )
    
    var body: some View {
        ZStack {
            NativeMapView(region: $region, markers: viewModel.familyMarkers, mapType: nativeMapType)
                .ignoresSafeArea(edges: .top)
            
            // Loading indicator and errors
            VStack {
                if let error = viewModel.errorMessage {
                    Text(error)
                        .padding()
                        .background(Color.red.opacity(0.8))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                        .padding()
                }
                
                Spacer()
                
                HStack {
                    Button(action: { showFamilySheet = true }) {
                        Image(systemName: "person.2.fill")
                            .font(.title2)
                            .padding()
                            .background(Color.white)
                            .clipShape(Circle())
                            .shadow(radius: 4)
                    }
                    .padding()
                    
                    Spacer()
                    Button(action: {
                        Task {
                            await viewModel.fetchPositions()
                            
                            // Auto-center on first marker if available
                            if let first = viewModel.familyMarkers.first {
                                withAnimation {
                                    region = MKCoordinateRegion(
                                        center: CLLocationCoordinate2D(latitude: first.lat, longitude: first.lng),
                                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
                                    )
                                }
                            }
                        }
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.title2)
                            .padding()
                            .background(Color.white)
                            .clipShape(Circle())
                            .shadow(radius: 4)
                    }
                    .padding()
                    .disabled(viewModel.isLoading)
                    .overlay(
                        Group {
                            if viewModel.isLoading {
                                ProgressView()
                                    .padding()
                            }
                        }
                    )
                }
            }
        }
        .toolbarBackground(.visible, for: .tabBar)
        .toolbarBackground(AppColors.surfaceDark, for: .tabBar)
        .onAppear {
            Task {
                await viewModel.fetchPositions()
            }
        }
        .sheet(isPresented: $showFamilySheet) {
            FamilyView()
        }
    }
}

struct MapView_Previews: PreviewProvider {
    static var previews: some View {
        MapView()
    }
}
