# Regras padrão do Room, Compose e Firebase já vêm nas bibliotecas (consumer rules).

# Credential Manager (login com Google) carrega a implementação do Play Services por reflexão.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
