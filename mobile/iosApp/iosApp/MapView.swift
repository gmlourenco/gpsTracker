import SwiftUI
import MapKit
import shared

struct MapView: View {
    @StateObject private var viewModel = MapViewModel()
    
    // Default region (fallback)
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 39.3999, longitude: -8.2245), // Portugal center
        span: MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)
    )
    
    var body: some View {
        ZStack {
            Map(coordinateRegion: $region, annotationItems: viewModel.familyMarkers) { marker in
                MapAnnotation(coordinate: CLLocationCoordinate2D(latitude: marker.lat, longitude: marker.lng)) {
                    VStack(spacing: 0) {
                        // Custom Marker View
                        ZStack {
                            Circle()
                                .fill(Color(marker.uiColor))
                                .frame(width: 36, height: 36)
                                .shadow(radius: 3)
                                .overlay(
                                    Circle().stroke(Color.white, lineWidth: 2)
                                )
                            
                            Text(marker.markerLetter)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        }
                        
                        // Pointer
                        Image(systemName: "triangle.fill")
                            .resizable()
                            .frame(width: 14, height: 10)
                            .foregroundColor(Color(marker.uiColor))
                            .rotationEffect(.degrees(180))
                            .offset(y: -2)
                            .padding(.bottom, 20) // to offset the pin correctly on the coordinate
                    }
                    .onTapGesture {
                        print("Tapped on \(marker.label)")
                    }
                }
            }
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
        .onAppear {
            Task {
                await viewModel.fetchPositions()
            }
        }
    }
}

struct MapView_Previews: PreviewProvider {
    static var previews: some View {
        MapView()
    }
}
