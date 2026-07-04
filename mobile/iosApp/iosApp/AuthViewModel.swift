import Foundation
import GoogleSignIn
import shared
import UIKit

@MainActor
class AuthViewModel: ObservableObject {
    @Published var isAuthenticated = false
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    
    private let platformDeps = IosPlatformDependencies()
    
    init() {
        checkSession()
    }
    
    func checkSession() {
        let jwt = platformDeps.getSupabaseJwt()
        isAuthenticated = (jwt != nil && !jwt!.isEmpty)
    }
    
    func signIn(presenting: UIViewController) {
        isLoading = true
        errorMessage = nil
        
        GIDSignIn.sharedInstance.signIn(withPresenting: presenting) { [weak self] signInResult, error in
            guard let self = self else { return }
            
            if let error = error {
                self.isLoading = false
                self.errorMessage = "Erro no Google Login: \(error.localizedDescription)"
                return
            }
            
            guard let user = signInResult?.user,
                  let idToken = user.idToken?.tokenString else {
                self.isLoading = false
                self.errorMessage = "Token de autenticação não encontrado."
                return
            }
            
            let accessToken = user.accessToken.tokenString
            
            Task {
                do {
                    try await KoinIOSKt.signInWithGoogleIdToken(idToken: idToken, accessToken: accessToken)
                    self.checkSession()
                    self.isLoading = false
                } catch {
                    self.isLoading = false
                    self.errorMessage = "Erro ao autenticar com o servidor: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func signOut() {
        GIDSignIn.sharedInstance.signOut()
        
        Task {
            do {
                try await KoinIOSKt.signOutFromSupabase()
            } catch {
                print("Failed to sign out from Supabase: \(error)")
            }
        }
        
        platformDeps.setSupabaseJwt(jwt: nil)
        isAuthenticated = false
    }
}
