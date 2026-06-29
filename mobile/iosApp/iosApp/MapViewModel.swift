import Foundation
import shared
import Combine
import CoreLocation

@MainActor
class MapViewModel: ObservableObject {
    @Published var familyMarkers: [FamilyDeviceMarker] = []
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    
    private let repository = KoinIOSKt.getFamilyPositionsRepository()
    
    func fetchPositions() async {
        isLoading = true
        errorMessage = nil
        
        do {
            // In KMP, suspend functions returning Result<T> are exposed to Swift.
            // Since Swift can't natively unpack kotlin.Result without helpers, 
            // we can call it and check if it's success.
            let result = try await repository.fetchLastPositions(historyCount: 10)
            
            // Kotlin Result maps to a class in Swift, we can check its state
            if let markers = result.getOrNull() as? [FamilyDeviceMarker] {
                self.familyMarkers = markers
            } else if let error = result.exceptionOrNull() {
                self.errorMessage = error.message ?? "Erro ao carregar posições."
            }
        } catch {
            self.errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
}

// Swift helper to extract colors
extension FamilyDeviceMarker {
    var uiColor: UIColor {
        return UIColor(hex: self.markerColorHex) ?? .systemGreen
    }
}

extension UIColor {
    convenience init?(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        var rgb: UInt64 = 0

        var r: CGFloat = 0.0
        var g: CGFloat = 0.0
        var b: CGFloat = 0.0
        var a: CGFloat = 1.0

        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else { return nil }

        if hexSanitized.count == 6 {
            r = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
            g = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
            b = CGFloat(rgb & 0x0000FF) / 255.0

        } else if hexSanitized.count == 8 {
            a = CGFloat((rgb & 0xFF000000) >> 24) / 255.0
            r = CGFloat((rgb & 0x00FF0000) >> 16) / 255.0
            g = CGFloat((rgb & 0x0000FF00) >> 8) / 255.0
            b = CGFloat(rgb & 0x000000FF) / 255.0

        } else {
            return nil
        }

        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
