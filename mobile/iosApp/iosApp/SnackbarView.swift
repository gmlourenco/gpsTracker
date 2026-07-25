import SwiftUI

struct SnackbarMessage: Equatable {
    let text: String
    let type: SnackbarType
    
    enum SnackbarType {
        case success
        case info
        case error
        
        var color: Color {
            switch self {
            case .success: return Color(hex: "#16A34A")
            case .info: return Color(hex: "#3B82F6")
            case .error: return Color.red
            }
        }
    }
}

struct SnackbarView: View {
    let message: SnackbarMessage
    
    var body: some View {
        HStack {
            Text(message.text)
                .font(.subheadline)
                .foregroundColor(.white)
                .multilineTextAlignment(.leading)
            Spacer()
        }
        .padding()
        .background(message.type.color)
        .cornerRadius(8)
        .shadow(radius: 4)
        .padding(.horizontal)
    }
}
