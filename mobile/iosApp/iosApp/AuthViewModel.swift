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
        
        let clientID = "522937712462-4vc4goged9jf34v5q26por5d063p2adv.apps.googleusercontent.com"
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        print("🟢 [AuthViewModel] Initiating Google Sign-In with Client ID: \(clientID)")
        
        GIDSignIn.sharedInstance.signIn(withPresenting: presenting) { [weak self] signInResult, error in
            Task { @MainActor in
                guard let self = self else { return }
                
                if let error = error {
                    print("❌ [AuthViewModel] Google Sign-In error: \(error)")
                    self.isLoading = false
                    self.errorMessage = "Erro no Google Login: \(error.localizedDescription)"
                    return
                }
                
                guard let user = signInResult?.user,
                      let idToken = user.idToken?.tokenString else {
                    print("❌ [AuthViewModel] ID Token missing in Google Sign-In result!")
                    self.isLoading = false
                    self.errorMessage = "Token de autenticação não encontrado."
                    return
                }
                
                let accessToken = user.accessToken.tokenString
                print("🟢 [AuthViewModel] Google Sign-In successful. ID Token obtained (length: \(idToken.count)).")
                
                do {
                    print("🟢 [AuthViewModel] Authenticating with Supabase server via KMP...")
                    try await KoinIOSKt.signInWithGoogleIdToken(idToken: idToken, accessToken: accessToken, nonce: nil)
                    print("🟢 [AuthViewModel] Successfully authenticated with Supabase server!")
                    self.checkSession()
                    self.isLoading = false
                } catch {
                    print("❌ [AuthViewModel] Server authentication error: \(error)")
                    self.isLoading = false
                    self.errorMessage = "Erro ao autenticar com o servidor: \(error.localizedDescription)"
                }
            }
        }
    }
    
    func signOut() {
        print("🟢 [AuthViewModel] Signing out from Google and Supabase...")
        GIDSignIn.sharedInstance.signOut()
        
        Task {
            do {
                try await KoinIOSKt.signOutFromSupabase()
                print("🟢 [AuthViewModel] Successfully signed out from Supabase.")
            } catch {
                print("❌ [AuthViewModel] Failed to sign out from Supabase: \(error)")
            }
        }
        
        platformDeps.setSupabaseJwt(jwt: nil)
        isAuthenticated = false
    }
}
