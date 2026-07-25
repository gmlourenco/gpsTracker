import SwiftUI
import shared



// ConfigCard Component
struct ConfigCard<Content: View>: View {
    let title: String
    let content: Content
    
    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundColor(.white)
                .padding(.bottom, 4)
            
            content
        }
        .padding()
        .background(Color(hex: "#16213E"))
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.2), radius: 4, x: 0, y: 2)
    }
}

extension View {
    func getRootViewController() -> UIViewController {
        guard let screen = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            return UIViewController()
        }
        guard let root = screen.windows.first?.rootViewController else {
            return UIViewController()
        }
        return root
    }
}

struct SettingsView: View {
    @StateObject private var authVM = AuthViewModel()
    
    // AppStorage variables
    @AppStorage("device_label") private var storedDeviceLabel: String = "Dispositivo"
    @AppStorage("tracking_distance_m") private var storedTrackingDistanceM: Double = 200.0
    @AppStorage("tracking_interval_ms") private var storedTrackingIntervalMs: Double = 60000.0
    @AppStorage("accident_sensor_sensitivity") private var storedAccidentSensorSensitivity: String = "medium"
    @AppStorage("default_map_type") private var storedDefaultMapType: String = "SATELLITE"
    @AppStorage("device_marker_color") private var storedMarkerColorHex: String = "#16A34A"
    
    // Local State (Prevents UI lag during typing)
    @State private var deviceLabel: String = ""
    @State private var trackingDistanceM: Double = 200.0
    @State private var trackingIntervalMs: Double = 60000.0
    @State private var defaultMapType: String = "SATELLITE"
    @State private var markerColorHex: String = "#16A34A"
    
    @State private var customSensitivityValue: String = ""
    @State private var selectedSensitivity: String = "Média"
    
    @State private var isSaving: Bool = false
    @State private var showCreateGroup: Bool = false
    @State private var showJoinGroup: Bool = false
    
    @State private var newGroupName: String = ""
    @State private var inviteCode: String = ""
    
    @State private var snackbarMessage: SnackbarMessage?
    
    @State private var actionTask: Task<Void, Never>?
    
    private var currentSensitivityValue: String {
        if selectedSensitivity == "Personalizada" {
            return "custom_\(customSensitivityValue.isEmpty ? "4" : customSensitivityValue)"
        } else if selectedSensitivity == "Alta" {
            return "alta"
        } else if selectedSensitivity == "Baixa" {
            return "baixa"
        }
        return "medium"
    }
    
    private var hasChanges: Bool {
        return deviceLabel != storedDeviceLabel ||
               trackingDistanceM != storedTrackingDistanceM ||
               trackingIntervalMs != storedTrackingIntervalMs ||
               defaultMapType != storedDefaultMapType ||
               markerColorHex != storedMarkerColorHex ||
               currentSensitivityValue != storedAccidentSensorSensitivity
    }
    
    let intervals: [Double] = [60000, 300000, 600000, 900000, 1800000, 3600000] // 1, 5, 10, 15, 30, 60 min
    let mapTypes = ["SATELLITE", "NORMAL", "TERRAIN"]
    let sensitivities = ["Alta", "Média", "Baixa", "Personalizada"]
    let colors = ["#16A34A", "#3B82F6", "#F97316", "#EF4444", "#A855F7"] // Green, Blue, Orange, Red, Purple
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Grupo Familiar
                ConfigCard(title: "Grupo Familiar") {
                    VStack(spacing: 12) {
                        if authVM.isAuthenticated {
                            Button(action: {
                                authVM.signOut()
                            }) {
                                HStack {
                                    Image(systemName: "arrow.right.circle.fill")
                                    Text("Terminar Sessão")
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.red)
                                .foregroundColor(.white)
                                .cornerRadius(8)
                            }
                        } else {
                            Button(action: {
                                authVM.signIn(presenting: getRootViewController())
                            }) {
                                HStack {
                                    Image(systemName: "g.circle.fill")
                                    Text(authVM.isLoading ? "A carregar..." : "Login com Google")
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color(hex: "#3B82F6"))
                                .foregroundColor(.white)
                                .cornerRadius(8)
                            }
                            .disabled(authVM.isLoading)
                        }
                        
                        if let error = authVM.errorMessage {
                            Text(error)
                                .foregroundColor(.red)
                                .font(.caption)
                        }
                        
                        HStack(spacing: 12) {
                            Button(action: {
                                showCreateGroup.toggle()
                                showJoinGroup = false
                            }) {
                                Text("Criar Novo Grupo")
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(Color(hex: "#16A34A"))
                                    .foregroundColor(.white)
                                    .cornerRadius(8)
                            }
                            
                            Button(action: {
                                showJoinGroup.toggle()
                                showCreateGroup = false
                            }) {
                                Text("Entrar por código")
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(Color.white.opacity(0.1))
                                    .foregroundColor(.white)
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                    )
                            }
                        }
                        
                        if showCreateGroup {
                            VStack(spacing: 8) {
                                TextField("Nome da Família", text: $newGroupName)
                                    .padding()
                                    .background(Color.white.opacity(0.05))
                                    .cornerRadius(8)
                                    .foregroundColor(.white)
                                
                                Button(action: {
                                    actionTask?.cancel()
                                    actionTask = Task {
                                        let _ = try? await KoinIOSKt.createFarm(name: newGroupName)
                                        if !Task.isCancelled {
                                            showCreateGroup = false
                                            newGroupName = ""
                                        }
                                    }
                                }) {
                                    Text("Confirmar")
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .background(Color(hex: "#16A34A"))
                                        .foregroundColor(.white)
                                        .cornerRadius(8)
                                }
                            }
                        }
                        if showJoinGroup {
                            VStack(spacing: 8) {
                                TextField("Código de Convite", text: $inviteCode)
                                    .padding()
                                    .background(Color.white.opacity(0.05))
                                    .cornerRadius(8)
                                    .foregroundColor(.white)
                                
                                Button(action: {
                                    actionTask?.cancel()
                                    actionTask = Task {
                                        let _ = try? await KoinIOSKt.joinFarm(inviteCode: inviteCode)
                                        if !Task.isCancelled {
                                            showJoinGroup = false
                                            inviteCode = ""
                                        }
                                    }
                                }) {
                                    Text("Entrar")
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .background(Color(hex: "#16A34A"))
                                        .foregroundColor(.white)
                                        .cornerRadius(8)
                                }
                            }
                        }
                    }
                }
                
                // Identidade do Dispositivo
                ConfigCard(title: "Identidade do Dispositivo") {
                    VStack(alignment: .leading, spacing: 16) {
                        TextField("Nome do Dispositivo", text: $deviceLabel)
                            .padding()
                            .background(Color.white.opacity(0.05))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                            .accentColor(Color(hex: "#16A34A"))
                        
                        Text("Cor do Marcador")
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.8))
                        
                        HStack(spacing: 16) {
                            ForEach(colors, id: \.self) { hexCode in
                                Circle()
                                    .fill(Color(hex: hexCode))
                                    .frame(width: 40, height: 40)
                                    .overlay(
                                        Circle()
                                            .stroke(Color.white, lineWidth: markerColorHex == hexCode ? 3 : 0)
                                    )
                                    .onTapGesture {
                                        markerColorHex = hexCode
                                    }
                            }
                        }
                    }
                }
                
                // Intervalo de Rastreio
                ConfigCard(title: "Intervalo de Rastreio") {
                    HStack {
                        Text("Atualizar a cada:")
                            .foregroundColor(.white)
                        Spacer()
                        Picker("Intervalo", selection: $trackingIntervalMs) {
                            ForEach(intervals, id: \.self) { interval in
                                Text("\(Int(interval / 60000)) minutos").tag(interval)
                            }
                        }
                        .pickerStyle(.menu)
                        .accentColor(Color(hex: "#16A34A"))
                    }
                }
                
                // Distância Mínima
                ConfigCard(title: "Distância Mínima") {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Atualizar após mover:")
                                .foregroundColor(.white)
                            Spacer()
                            Text("\(Int(trackingDistanceM)) m")
                                .foregroundColor(Color(hex: "#16A34A"))
                                .bold()
                        }
                        
                        Slider(value: $trackingDistanceM, in: 50...500, step: 50)
                            .accentColor(Color(hex: "#16A34A"))
                    }
                }
                
                // Tipo de Mapa Padrão
                ConfigCard(title: "Tipo de Mapa Padrão") {
                    HStack {
                        Text("Visualização:")
                            .foregroundColor(.white)
                        Spacer()
                        Picker("Tipo de Mapa", selection: $defaultMapType) {
                            ForEach(mapTypes, id: \.self) { type in
                                Text(type.capitalized).tag(type)
                            }
                        }
                        .pickerStyle(.menu)
                        .accentColor(Color(hex: "#16A34A"))
                    }
                }
                
                // Segurança & Acidentes
                ConfigCard(title: "Segurança & Acidentes") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Sensibilidade:")
                                .foregroundColor(.white)
                            Spacer()
                            Picker("Sensibilidade", selection: $selectedSensitivity) {
                                ForEach(sensitivities, id: \.self) { sensitivity in
                                    Text(sensitivity).tag(sensitivity)
                                }
                            }
                            .pickerStyle(.menu)
                            .accentColor(Color(hex: "#16A34A"))
                        }
                        
                        if selectedSensitivity == "Personalizada" {
                            HStack {
                                Text("G-Force (1-99):")
                                    .foregroundColor(.white.opacity(0.8))
                                Spacer()
                                TextField("Ex: 4", text: $customSensitivityValue)
                                    .keyboardType(.numberPad)
                                    .multilineTextAlignment(.trailing)
                                    .padding(8)
                                    .frame(width: 80)
                                    .background(Color.white.opacity(0.05))
                                    .cornerRadius(8)
                                    .foregroundColor(.white)
                            }
                        }
                    }
                }
                
                // Guardar
                Button(action: {
                    saveSettings()
                }) {
                    if isSaving {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color(hex: "#16A34A"))
                            .cornerRadius(12)
                    } else {
                        Text("Guardar")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(hasChanges ? Color(hex: "#16A34A") : Color.gray)
                            .foregroundColor(hasChanges ? .white : .white.opacity(0.6))
                            .cornerRadius(12)
                    }
                }
                .disabled(isSaving || !hasChanges)
                .padding(.top, 10)
            }
            .padding()
        }
        .background(Color(hex: "#1A1A2E").edgesIgnoringSafeArea(.all))
        .overlay(
            VStack {
                Spacer()
                if let msg = snackbarMessage {
                    SnackbarView(message: msg)
                        .padding(.bottom, 20)
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                                withAnimation {
                                    if snackbarMessage == msg {
                                        snackbarMessage = nil
                                    }
                                }
                            }
                        }
                }
            }
        )
        .onAppear {
            deviceLabel = storedDeviceLabel
            trackingDistanceM = storedTrackingDistanceM
            trackingIntervalMs = storedTrackingIntervalMs
            defaultMapType = storedDefaultMapType
            markerColorHex = storedMarkerColorHex
            
            let sensitivity = storedAccidentSensorSensitivity
            if sensitivity.hasPrefix("custom_") {
                selectedSensitivity = "Personalizada"
                customSensitivityValue = String(sensitivity.dropFirst("custom_".count))
            } else if sensitivity == "alta" {
                selectedSensitivity = "Alta"
            } else if sensitivity == "baixa" {
                selectedSensitivity = "Baixa"
            } else if sensitivity == "Personalizada" { // Legacy fallback
                selectedSensitivity = "Personalizada"
            } else {
                selectedSensitivity = "Média"
            }
        }
        .onDisappear {
            actionTask?.cancel()
        }
    }
    
    private func saveSettings() {
        actionTask?.cancel()
        isSaving = true
        snackbarMessage = nil
        
        let sensitivityToSave = currentSensitivityValue
        let labelToSave = deviceLabel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Dispositivo" : deviceLabel
        let colorToSave = markerColorHex
        let distanceToSave = trackingDistanceM
        let intervalToSave = trackingIntervalMs
        let mapTypeToSave = defaultMapType
        
        // Optimistically apply local state to AppStorage
        storedDeviceLabel = labelToSave
        storedTrackingDistanceM = distanceToSave
        storedTrackingIntervalMs = intervalToSave
        storedDefaultMapType = mapTypeToSave
        storedMarkerColorHex = colorToSave
        storedAccidentSensorSensitivity = sensitivityToSave
        
        actionTask = Task {
            do {
                // First sync the farm identity (which updates farm backend)
                let _ = try await KoinIOSKt.syncCurrentDeviceToFarm()
                
                // Now sync all full settings via DeviceConfigRepository
                let repo = KoinIOSKt.getDeviceConfigRepository()
                
                let dto = DeviceConfigDto(
                    serialNumber: UIDevice.current.identifierForVendor?.uuidString ?? "ios-device",
                    deviceLabel: labelToSave,
                    markerColor: colorToSave,
                    trackingIntervalMs: Int64(intervalToSave),
                    trackingDistanceM: Float(distanceToSave),
                    defaultMapType: mapTypeToSave,
                    accidentSensorSensitivity: sensitivityToSave,
                    configUpdatedAt: Int64(Date().timeIntervalSince1970 * 1000)
                )
                
                let result = try await repo.saveConfigToBackend(config: dto)
                
                await MainActor.run {
                    isSaving = false
                    if result == .success {
                        snackbarMessage = SnackbarMessage(text: "Configurações guardadas e sincronizadas", type: .success)
                    } else if result == .offlineQueued {
                        snackbarMessage = SnackbarMessage(text: "Guardado localmente (sincronizará quando tiver rede)", type: .info)
                    } else {
                        snackbarMessage = SnackbarMessage(text: "Erro ao guardar definições no servidor", type: .error)
                    }
                }
            } catch {
                await MainActor.run {
                    isSaving = false
                    snackbarMessage = SnackbarMessage(text: "Falha ao sincronizar: \(error.localizedDescription)", type: .error)
                }
            }
        }
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
    }
}
