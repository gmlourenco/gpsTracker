import SwiftUI
import shared

// MARK: - Models
enum FamilyRole: String {
    case creator = "Criador"
    case admin = "Admin"
    case member = "Membro"
}

struct FamilyMember: Identifiable {
    let id = UUID()
    let userId: String
    let name: String
    let initial: String
    let colorHex: String
    let role: FamilyRole
    let isCurrentUser: Bool
}

// MARK: - ViewModel
@MainActor
class FamilyViewModel: ObservableObject {
    @Published var members: [FamilyMember] = []
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    
    @Published var selectedFarmName: String = "Carregando..."
    @Published var inviteCode: String = ""
    @Published var showAddJoinSection: Bool = false
    @Published var newFarmName: String = ""
    @Published var newInviteCode: String = ""
    
    @Published var availableFarms: [String] = []
    
    private var currentFarmId: String = ""
    
    private var loadTask: Task<Void, Never>?
    
    init() {
        loadFarms()
    }
    
    func loadFarms() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            guard let self = self else { return }
            self.isLoading = true
            errorMessage = nil
            do {
                let response = try await KoinIOSKt.fetchMyFarms()
                if response.success, !response.farms.isEmpty {
                    let farm = response.farms.first!
                    self.currentFarmId = farm.farmId
                    self.selectedFarmName = farm.farmName
                    self.inviteCode = farm.inviteCode ?? ""
                    self.availableFarms = response.farms.map { $0.farmName }
                    
                    self.members = farm.members.map { dto in
                        let role: FamilyRole
                        if dto.isCreator { role = .creator }
                        else if dto.isAdmin { role = .admin }
                        else { role = .member }
                        
                        return FamilyMember(
                            userId: dto.resolvedUserId,
                            name: dto.displayName ?? "Utilizador",
                            initial: String((dto.displayName ?? "U").prefix(1)),
                            colorHex: "#3B82F6",
                            role: role,
                            isCurrentUser: dto.resolvedUserId == response.currentUserId
                        )
                    }
                } else {
                    self.errorMessage = response.error ?? "Nenhuma família encontrada."
                }
            } catch {
                self.errorMessage = "Erro ao carregar família: \(error.localizedDescription)"
                print("Failed to load farms: \(error)")
            }
            if !Task.isCancelled {
                self.isLoading = false
            }
        }
    }
    
    // Actions
    func kickMember(_ member: FamilyMember) {
        Task { [weak self] in
            guard let self = self else { return }
            let _ = try? await KoinIOSKt.manageMember(farmId: self.currentFarmId, targetUserId: member.userId, action: "kick")
            self.loadFarms()
        }
    }
    
    func promoteMember(_ member: FamilyMember) {
        Task { [weak self] in
            guard let self = self else { return }
            let _ = try? await KoinIOSKt.manageMember(farmId: self.currentFarmId, targetUserId: member.userId, action: "promote_admin")
            self.loadFarms()
        }
    }
    
    func demoteMember(_ member: FamilyMember) {
        Task { [weak self] in
            guard let self = self else { return }
            let _ = try? await KoinIOSKt.manageMember(farmId: self.currentFarmId, targetUserId: member.userId, action: "demote_admin")
            self.loadFarms()
        }
    }
    
    func createFarm() {
        Task { [weak self] in
            guard let self = self else { return }
            let _ = try? await KoinIOSKt.createFarm(name: self.newFarmName)
            self.loadFarms()
        }
    }
    
    func joinFarm() {
        Task { [weak self] in
            guard let self = self else { return }
            let _ = try? await KoinIOSKt.joinFarm(inviteCode: self.newInviteCode)
            self.loadFarms()
        }
    }
}



// MARK: - Views
struct FamilyView: View {
    @StateObject private var viewModel = FamilyViewModel()
    
    var body: some View {
        NavigationView {
            ZStack {
                AppColors.surfaceDark.ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Top Section
                    topSection
                        .padding()
                    
                    // Members List
                    List {
                        ForEach(viewModel.members) { member in
                            MemberRowView(member: member)
                                .listRowBackground(AppColors.surfaceDark)
                                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                                .listRowSeparator(.hidden)
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    if !member.isCurrentUser {
                                        // Destructive Action
                                        Button(role: .destructive) {
                                            withAnimation {
                                                viewModel.kickMember(member)
                                            }
                                        } label: {
                                            Label("Remover", systemImage: "trash")
                                        }
                                        .tint(AppColors.sosRed)
                                        
                                        // Promote / Demote Action
                                        if member.role == .member {
                                            Button {
                                                withAnimation {
                                                    viewModel.promoteMember(member)
                                                }
                                            } label: {
                                                Label("Promover", systemImage: "arrow.up.circle")
                                            }
                                            .tint(.blue)
                                        } else if member.role == .admin {
                                            Button {
                                                withAnimation {
                                                    viewModel.demoteMember(member)
                                                }
                                            } label: {
                                                Label("Despromover", systemImage: "arrow.down.circle")
                                            }
                                            .tint(.orange)
                                        }
                                    }
                                }
                        }
                    }
                    .listStyle(.plain)
                    .refreshable {
                        viewModel.loadFarms()
                    }
                    
                    if let error = viewModel.errorMessage {
                        Text(error)
                            .foregroundColor(AppColors.sosRed)
                            .font(.caption)
                            .padding(.vertical, 8)
                    }
                    
                    // Bottom Add/Join Section Toggle
                    Button(action: {
                        withAnimation {
                            viewModel.showAddJoinSection.toggle()
                        }
                    }) {
                        HStack {
                            Image(systemName: viewModel.showAddJoinSection ? "chevron.down" : "chevron.up")
                            Text(viewModel.showAddJoinSection ? "Ocultar Opções" : "Gerir Famílias")
                        }
                        .font(.subheadline.bold())
                        .foregroundColor(AppColors.textSecondary)
                        .padding(.vertical, 8)
                    }
                    
                    if viewModel.showAddJoinSection {
                        addJoinSection
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
            }
            .navigationBarHidden(true)
        }
    }
    
    private var topSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Farm Selector
            HStack {
                Text("Família Ativa")
                    .font(.subheadline)
                    .foregroundColor(AppColors.textSecondary)
                
                Spacer()
                
                Menu {
                    ForEach(viewModel.availableFarms, id: \.self) { farm in
                        Button(action: { viewModel.selectedFarmName = farm }) {
                            Text(farm)
                            if farm == viewModel.selectedFarmName {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                } label: {
                    HStack {
                        Text(viewModel.selectedFarmName)
                            .font(.headline)
                            .foregroundColor(AppColors.textPrimary)
                        Image(systemName: "chevron.down")
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(AppColors.cardDark)
                    .cornerRadius(8)
                }
            }
            
            // Invite Code Card
            VStack(spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Código de Convite")
                            .font(.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                        Text(viewModel.inviteCode)
                            .font(.system(size: 32, weight: .bold, design: .monospaced))
                            .foregroundColor(AppColors.textPrimary)
                            .tracking(4)
                    }
                    
                    Spacer()
                    
                    ShareLink(item: "Junta-te à minha família no Tracker App! Código: \(viewModel.inviteCode)") {
                        Image(systemName: "square.and.arrow.up")
                            .font(.title2)
                            .foregroundColor(AppColors.textPrimary)
                            .padding(12)
                            .background(AppColors.surfaceDark)
                            .clipShape(Circle())
                    }
                }
                
                HStack {
                    Text("Expira em 3 dias • 5 usos restantes")
                        .font(.caption)
                        .foregroundColor(AppColors.textSecondary)
                    Spacer()
                }
            }
            .padding(20)
            .background(AppColors.cardDark)
            .cornerRadius(16)
        }
    }
    
    private var addJoinSection: some View {
        VStack(spacing: 16) {
            // Create Section
            VStack(spacing: 12) {
                TextField("", text: $viewModel.newFarmName, prompt: Text("Nome da nova Família").foregroundColor(AppColors.textSecondary))
                    .foregroundColor(AppColors.textPrimary)
                    .padding()
                    .background(AppColors.surfaceDark)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(AppColors.textSecondary.opacity(0.3), lineWidth: 1)
                    )
                
                Button(action: {
                    viewModel.createFarm()
                }) {
                    Text("Criar Família")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(AppColors.accentGreen)
                        .cornerRadius(8)
                }
            }
            
            Divider()
                .background(AppColors.textSecondary.opacity(0.3))
            
            // Join Section
            VStack(spacing: 12) {
                TextField("", text: $viewModel.newInviteCode, prompt: Text("Código de Convite").foregroundColor(AppColors.textSecondary))
                    .foregroundColor(AppColors.textPrimary)
                    .padding()
                    .background(AppColors.surfaceDark)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(AppColors.textSecondary.opacity(0.3), lineWidth: 1)
                    )
                
                Button(action: {
                    viewModel.joinFarm()
                }) {
                    Text("Juntar")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(8)
                }
            }
        }
        .padding(20)
        .background(AppColors.cardDark)
        .cornerRadius(24, corners: [.topLeft, .topRight])
        .shadow(color: Color.black.opacity(0.2), radius: 10, y: -5)
    }
}

// MARK: - Subviews
struct MemberRowView: View {
    let member: FamilyMember
    
    var body: some View {
        HStack(spacing: 16) {
            // Avatar
            ZStack {
                Circle()
                    .fill(Color(hex: member.colorHex))
                    .frame(width: 48, height: 48)
                
                Text(member.initial)
                    .font(.title3.bold())
                    .foregroundColor(.white)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(member.name)
                        .font(.headline)
                        .foregroundColor(AppColors.textPrimary)
                    
                    if member.isCurrentUser {
                        Text("(Tu)")
                            .font(.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }
                
                // Role Pill
                if member.role != .member {
                    Text(member.role.rawValue)
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(roleColor.opacity(0.2))
                        .foregroundColor(roleColor)
                        .clipShape(Capsule())
                } else {
                    Text(member.role.rawValue)
                        .font(.subheadline)
                        .foregroundColor(AppColors.textSecondary)
                }
            }
            
            Spacer()
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 16)
        .background(AppColors.cardDark)
        .cornerRadius(12)
    }
    
    private var roleColor: Color {
        switch member.role {
        case .creator: return AppColors.accentGreen
        case .admin: return .blue
        case .member: return AppColors.textSecondary
        }
    }
}

// Utility to round specific corners
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape( RoundedCorner(radius: radius, corners: corners) )
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}

struct FamilyView_Previews: PreviewProvider {
    static var previews: some View {
        FamilyView()
    }
}
