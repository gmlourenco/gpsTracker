import SwiftUI
import Combine
import shared

// MARK: - ViewModel
@MainActor
class HomeViewModel: ObservableObject {
    @Published var isTracking: Bool = false
    @Published var isSosActive: Bool = false
    @Published var isPreSosActive: Bool = false
    @Published var preSosCountdown: Int = 15
    @Published var showDeactivateSheet: Bool = false
    @Published var isOnline: Bool = true
    @Published var gpsAccuracy: String = "4.2m"
    @Published var verificationCode: String = ""
    @Published var userInputCode: String = ""
    
    private var countdownTimer: AnyCancellable?
    private var cancellables = Set<AnyCancellable>()
    private var flowWatcher: shared.Closeable?
    private var sosWatcher: shared.Closeable?
    
    init() {
        generateNewCode()
        
        // Bind to LocationService tracking state
        LocationService.shared.$isTracking
            .receive(on: RunLoop.main)
            .assign(to: &$isTracking)
            
        // Observe KMP CFlow for offline sync count
        let repo = KoinIOSKt.getTelemetryRepository()
        let cflow = CFlowKt.asCFlow(repo.getUnsyncedCountFlow())
        flowWatcher = cflow.watch { [weak self] (count: Any?) in
            guard let self = self, let intCount = count as? Int else { return }
            DispatchQueue.main.async {
                self.isOnline = (intCount == 0)
            }
        }
        
        sosWatcher = TrackingStateRepository.shared.observeIsSosActive().watch { [weak self] active in
            guard let active = active?.boolValue else { return }
            DispatchQueue.main.async {
                withAnimation {
                    self?.isSosActive = active
                }
            }
        }
    }
    
    func toggleTracking(isOn: Bool) {
        if isOn {
            LocationService.shared.startTracking()
        } else {
            LocationService.shared.stopTracking()
        }
    }
    
    func generateNewCode() {
        let random = Int.random(in: 100000...999999)
        verificationCode = "\(random)"
        userInputCode = ""
    }
    
    func handleSosLongPress() {
        if isSosActive {
            generateNewCode()
            showDeactivateSheet = true
        } else {
            TrackingStateRepository.shared.setSosActive(active: true)
        }
    }
    
    func confirmDeactivation() {
        if userInputCode == verificationCode {
            TrackingStateRepository.shared.setSosActive(active: false)
            showDeactivateSheet = false
        }
    }
    
    func triggerPreSos() {
        isPreSosActive = true
        preSosCountdown = 15
        countdownTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] _ in
            guard let self = self else { return }
            if self.preSosCountdown > 0 {
                self.preSosCountdown -= 1
            } else {
                self.activateSosFromPreSos()
            }
        }
    }
    
    func cancelPreSos() {
        countdownTimer?.cancel()
        withAnimation {
            isPreSosActive = false
        }
    }
    
    func activateSosFromPreSos() {
        countdownTimer?.cancel()
        withAnimation {
            isPreSosActive = false
        }
        TrackingStateRepository.shared.setSosActive(active: true)
    }
}

// MARK: - Components

struct SOSButton: View {
    @ObservedObject var viewModel: HomeViewModel
    @State private var isPressing = false
    @State private var pressProgress: CGFloat = 0.0
    @State private var pulseState = false
    
    var body: some View {
        ZStack {
            // Radar pulses
            if viewModel.isSosActive {
                Circle()
                    .stroke(AppColors.sosRed.opacity(0.5), lineWidth: 2)
                    .frame(width: pulseState ? 300 : 150, height: pulseState ? 300 : 150)
                    .opacity(pulseState ? 0 : 1)
                    .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false), value: pulseState)
                    .onAppear { pulseState = true }
                
                Circle()
                    .stroke(AppColors.sosRed.opacity(0.3), lineWidth: 2)
                    .frame(width: pulseState ? 400 : 150, height: pulseState ? 400 : 150)
                    .opacity(pulseState ? 0 : 1)
                    .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false).delay(0.5), value: pulseState)
            }
            
            // Progress ring for long press
            if isPressing {
                Circle()
                    .trim(from: 0, to: pressProgress)
                    .stroke(AppColors.textPrimary, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .frame(width: 180, height: 180)
                    .rotationEffect(.degrees(-90))
                    .animation(.linear(duration: viewModel.isSosActive ? 1.0 : 0.2), value: pressProgress)
            }
            
            // Main Button
            Circle()
                .fill(viewModel.isSosActive ? AppColors.sosRed : AppColors.cardDark)
                .frame(width: 150, height: 150)
                .overlay(
                    Circle().stroke(AppColors.sosRed, lineWidth: 4)
                )
                .shadow(color: AppColors.sosRed.opacity(viewModel.isSosActive ? 0.8 : 0.2), radius: 15)
                .overlay(
                    Text("SOS")
                        .font(.system(size: 40, weight: .bold))
                        .foregroundColor(AppColors.textPrimary)
                )
                .onLongPressGesture(minimumDuration: viewModel.isSosActive ? 1.0 : 0.2, maximumDistance: 50) {
                    isPressing = false
                    pressProgress = 0.0
                    viewModel.handleSosLongPress()
                } onPressingChanged: { pressing in
                    isPressing = pressing
                    if pressing {
                        withAnimation(.linear(duration: viewModel.isSosActive ? 1.0 : 0.2)) {
                            pressProgress = 1.0
                        }
                    } else {
                        withAnimation(.easeOut(duration: 0.2)) {
                            pressProgress = 0.0
                        }
                    }
                }
        }
        .frame(maxWidth: .infinity)
    }
}

struct PreSosOverlay: View {
    @ObservedObject var viewModel: HomeViewModel
    @State private var pulseText = false
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.95).ignoresSafeArea()
            
            VStack(spacing: 40) {
                Text("🚨 ACIDENTE DETECTADO 🚨")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(AppColors.sosRed)
                    .multilineTextAlignment(.center)
                    .scaleEffect(pulseText ? 1.1 : 1.0)
                    .opacity(pulseText ? 1.0 : 0.7)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) {
                            pulseText = true
                        }
                    }
                
                // Countdown Ring
                ZStack {
                    Circle()
                        .stroke(AppColors.cardDark, lineWidth: 15)
                        .frame(width: 200, height: 200)
                    
                    Circle()
                        .trim(from: 0, to: CGFloat(viewModel.preSosCountdown) / 15.0)
                        .stroke(AppColors.sosRed, style: StrokeStyle(lineWidth: 15, lineCap: .round))
                        .frame(width: 200, height: 200)
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 1.0), value: viewModel.preSosCountdown)
                    
                    Text("\(viewModel.preSosCountdown)")
                        .font(.system(size: 60, weight: .bold))
                        .foregroundColor(AppColors.textPrimary)
                }
                
                VStack(spacing: 20) {
                    Button(action: {
                        viewModel.cancelPreSos()
                    }) {
                        Text("FALSO ALARME")
                            .font(.headline)
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(AppColors.onlineGreen)
                            .foregroundColor(.white)
                            .cornerRadius(15)
                    }
                    
                    Button(action: {
                        viewModel.activateSosFromPreSos()
                    }) {
                        Text("ATIVAR SOS IMEDIATAMENTE")
                            .font(.headline)
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(AppColors.sosRed)
                            .foregroundColor(.white)
                            .cornerRadius(15)
                    }
                }
                .padding(.horizontal, 30)
            }
        }
    }
}

struct DeactivateSheet: View {
    @ObservedObject var viewModel: HomeViewModel
    
    var body: some View {
        ZStack {
            AppColors.surfaceDark.ignoresSafeArea()
            
            VStack(spacing: 30) {
                Text("Desativar SOS")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(AppColors.textPrimary)
                
                Text("Para desativar o alerta SOS, insira o código de segurança abaixo:")
                    .multilineTextAlignment(.center)
                    .foregroundColor(AppColors.textSecondary)
                    .padding(.horizontal)
                
                Text(viewModel.verificationCode)
                    .font(.system(size: 50, weight: .black, design: .monospaced))
                    .foregroundColor(AppColors.sosRed)
                    .tracking(10)
                
                TextField("Inserir Código", text: $viewModel.userInputCode)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .font(.title)
                    .padding()
                    .background(AppColors.cardDark)
                    .cornerRadius(10)
                    .foregroundColor(AppColors.textPrimary)
                    .padding(.horizontal, 40)
                
                HStack(spacing: 20) {
                    Button(action: {
                        viewModel.showDeactivateSheet = false
                    }) {
                        Text("Cancelar")
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(AppColors.cardDark)
                            .foregroundColor(AppColors.textPrimary)
                            .cornerRadius(15)
                    }
                    
                    Button(action: {
                        viewModel.confirmDeactivation()
                    }) {
                        Text("Confirmar")
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(AppColors.sosRed)
                            .foregroundColor(.white)
                            .cornerRadius(15)
                    }
                    .disabled(viewModel.userInputCode != viewModel.verificationCode)
                    .opacity(viewModel.userInputCode == viewModel.verificationCode ? 1.0 : 0.5)
                }
                .padding(.horizontal, 30)
                
                Spacer()
            }
            .padding(.top, 40)
        }
    }
}

// MARK: - Main View

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    
    var body: some View {
        ZStack {
            AppColors.surfaceDark.ignoresSafeArea()
            
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 5) {
                    Text("🚜 Segurança Rural")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(AppColors.textPrimary)
                    
                    Text(viewModel.isSosActive ? "Modo de Emergência" : "Monitorização Ativa")
                        .font(.subheadline)
                        .foregroundColor(viewModel.isSosActive ? AppColors.sosRed : AppColors.textSecondary)
                }
                .padding(.top, 20)
                
                // Tracking Toggle
                HStack {
                    VStack(alignment: .leading) {
                        Text(viewModel.isTracking ? "Rastreio Ativo" : "Rastreio Inativo")
                            .font(.headline)
                            .foregroundColor(AppColors.textPrimary)
                        Text("Atualização em tempo real")
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                    }
                    Spacer()
                    Toggle("", isOn: Binding(
                        get: { viewModel.isTracking },
                        set: { viewModel.toggleTracking(isOn: $0) }
                    ))
                        .toggleStyle(SwitchToggleStyle(tint: AppColors.onlineGreen))
                        .labelsHidden()
                }
                .padding()
                .background(AppColors.cardDark)
                .cornerRadius(15)
                .padding(.horizontal)
                
                Spacer()
                
                // SOS Box
                SOSButton(viewModel: viewModel)
                
                Spacer()
                
                // Status Tiles
                HStack(spacing: 15) {
                    // Online Status
                    VStack {
                        Circle()
                            .fill(viewModel.isOnline ? AppColors.onlineGreen : AppColors.amber)
                            .frame(width: 15, height: 15)
                        Text(viewModel.isOnline ? "ONLINE" : "OFFLINE")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(AppColors.textPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppColors.cardDark)
                    .cornerRadius(15)
                    
                    // GPS Accuracy
                    VStack {
                        Image(systemName: "location.fill")
                            .foregroundColor(AppColors.textSecondary)
                        Text(viewModel.gpsAccuracy)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(AppColors.textPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppColors.cardDark)
                    .cornerRadius(15)
                }
                .padding(.horizontal)
                .padding(.bottom, 30)
                
                // For demo purposes, allow triggering Pre-SOS by tapping a hidden area or keep a small button
                Button("Simular Acidente (Demo)") {
                    viewModel.triggerPreSos()
                }
                .foregroundColor(AppColors.textSecondary.opacity(0.5))
                .font(.caption2)
                .padding(.bottom, 10)
            }
            
            if viewModel.isPreSosActive {
                PreSosOverlay(viewModel: viewModel)
                    .zIndex(1)
            }
        }
        .sheet(isPresented: $viewModel.showDeactivateSheet) {
            DeactivateSheet(viewModel: viewModel)
        }
    }
}

struct HomeView_Previews: PreviewProvider {
    static var previews: some View {
        HomeView()
    }
}
