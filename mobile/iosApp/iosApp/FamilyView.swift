import SwiftUI

// MARK: - Models
enum FamilyRole: String {
    case creator = "Criador"
    case admin = "Admin"
    case member = "Membro"
}

struct FamilyMember: Identifiable {
    let id = UUID()
    let name: String
    let initial: String
    let colorHex: String
    let role: FamilyRole
    let isCurrentUser: Bool
}

// MARK: - ViewModel
@MainActor
class FamilyViewModel: ObservableObject {
    @Published var members: [FamilyMember] = [
        FamilyMember(name: "Gonçalo Lourenço", initial: "G", colorHex: "#3B82F6", role: .creator, isCurrentUser: true),
        FamilyMember(name: "Maria Silva", initial: "M", colorHex: "#EC4899", role: .admin, isCurrentUser: false),
        FamilyMember(name: "João Pedro", initial: "J", colorHex: "#F59E0B", role: .member, isCurrentUser: false),
        FamilyMember(name: "Ana Rita", initial: "A", colorHex: "#10B981", role: .member, isCurrentUser: false)
    ]
    
    @Published var selectedFarmName: String = "Farm Principal"
    @Published var inviteCode: String = "X7K9P2"
    @Published var showAddJoinSection: Bool = false
    @Published var newFarmName: String = ""
    @Published var newInviteCode: String = ""
    
    let availableFarms = ["Farm Principal", "Férias 2026", "Família Alargada"]
    
    // Actions
    func kickMember(_ member: FamilyMember) {
        if let index = members.firstIndex(where: { $0.id == member.id }) {
            members.remove(at: index)
        }
    }
    
    func promoteMember(_ member: FamilyMember) {
        // Mock promotion logic
        if let index = members.firstIndex(where: { $0.id == member.id }) {
            let newRole: FamilyRole = member.role == .member ? .admin : .creator
            let updated = FamilyMember(name: member.name, initial: member.initial, colorHex: member.colorHex, role: newRole, isCurrentUser: member.isCurrentUser)
            members[index] = updated
        }
    }
    
    func demoteMember(_ member: FamilyMember) {
        // Mock demotion logic
        if let index = members.firstIndex(where: { $0.id == member.id }) {
            let newRole: FamilyRole = member.role == .admin ? .member : .member
            let updated = FamilyMember(name: member.name, initial: member.initial, colorHex: member.colorHex, role: newRole, isCurrentUser: member.isCurrentUser)
            members[index] = updated
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
                    // Mock Create Action
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
                    // Mock Join Action
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
