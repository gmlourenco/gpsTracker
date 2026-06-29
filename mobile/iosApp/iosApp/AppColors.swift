import SwiftUI

// MARK: - App Colors
struct AppColors {
    static let surfaceDark = Color(hex: "#1A1A2E")
    static let cardDark = Color(hex: "#16213E")
    static let accentGreen = Color(hex: "#16A34A")
    static let onlineGreen = Color(hex: "#16A34A")
    static let textPrimary = Color(hex: "#F1F5F9")
    static let textSecondary = Color(hex: "#94A3B8")
    static let sosRed = Color(hex: "#DC2626")
    static let amber = Color.orange
}

// Extension to handle hex colors
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 255, 255, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
