import SwiftUI



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

struct SettingsView: View {
    // AppStorage variables
    @AppStorage("device_label") private var deviceLabel: String = "Dispositivo"
    @AppStorage("tracking_distance_m") private var trackingDistanceM: Double = 200.0
    @AppStorage("tracking_interval_ms") private var trackingIntervalMs: Double = 60000.0
    @AppStorage("accident_sensor_sensitivity") private var accidentSensorSensitivity: String = "medium"
    @AppStorage("default_map_type") private var defaultMapType: String = "SATELLITE"
    @AppStorage("marker_color_hex") private var markerColorHex: String = "#16A34A"
    
    @State private var customSensitivityValue: String = ""
    @State private var isSaving: Bool = false
    @State private var showCreateGroup: Bool = false
    @State private var showJoinGroup: Bool = false
    
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
                        Button(action: {
                            // Mock Google Login
                        }) {
                            HStack {
                                Image(systemName: "g.circle.fill")
                                Text("Login com Google")
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color(hex: "#3B82F6"))
                            .foregroundColor(.white)
                            .cornerRadius(8)
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
                            Text("Mock: Criação de Grupo...")
                                .foregroundColor(.gray)
                                .font(.caption)
                        }
                        if showJoinGroup {
                            Text("Mock: Entrar por código...")
                                .foregroundColor(.gray)
                                .font(.caption)
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
                            Picker("Sensibilidade", selection: $accidentSensorSensitivity) {
                                ForEach(sensitivities, id: \.self) { sensitivity in
                                    Text(sensitivity).tag(sensitivity)
                                }
                            }
                            .pickerStyle(.menu)
                            .accentColor(Color(hex: "#16A34A"))
                        }
                        
                        if accidentSensorSensitivity == "Personalizada" {
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
                            .background(Color(hex: "#16A34A"))
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                }
                .disabled(isSaving)
                .padding(.top, 10)
            }
            .padding()
        }
        .background(Color(hex: "#1A1A2E").edgesIgnoringSafeArea(.all))
    }
    
    private func saveSettings() {
        isSaving = true
        Task {
            // Simulate network sync
            try? await Task.sleep(nanoseconds: 1_500_000_000) // 1.5 seconds
            await MainActor.run {
                isSaving = false
            }
        }
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
    }
}
