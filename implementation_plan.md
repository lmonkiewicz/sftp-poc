# SFTP PoC — Spring Boot 4.x + Spring Integration

Aplikacja do pobierania plików z serwerów SFTP z obsługą wielu użytkowników. Każdy użytkownik ma własny adres SFTP, folder domowy i ścieżki input/output. Uwierzytelnianie kluczem SSH (private key).

## User Review Required

> [!IMPORTANT]
> **SSLBundles vs SSH Keys**: SFTP działa na protokole SSH, nie TLS. `SslBundles` w Spring Boot dotyczą certyfikatów X.509 dla HTTPS/TLS. Uwierzytelnianie SFTP opiera się na kluczach SSH (private key + passphrase), co jest natywnie wspierane przez `DefaultSftpSessionFactory.setPrivateKey()`. Dlatego **nie korzystamy z SSLBundles**, a konfigurujemy ścieżki do kluczy SSH per użytkownik.

> [!WARNING]
> Spring Boot 4.0 korzysta ze Spring Integration 7.0, która pod spodem używa Apache MINA SSHD zamiast JSch. To ważne przy debugowaniu problemów z kluczami — format klucza musi być kompatybilny z MINA SSHD (OpenSSH format jest OK).

---

## Proposed Changes

### Project Bootstrap

Wygenerowanie projektu Spring Boot 4.0 z Maven, Java 21 i zależnościami `spring-integration` + `spring-boot-starter`.

#### [NEW] Projekt via start.spring.io

```bash
curl -G https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=4.0.4 \
  -d groupId=dev.effective \
  -d artifactId=sftp-poc \
  -d name=sftp-poc \
  -d packageName=dev.effective.sftppoc \
  -d javaVersion=21 \
  -d dependencies=integration \
  -o /tmp/sftp-poc.zip
```

Dodanie `spring-integration-sftp` do `pom.xml` (nie jest dostępne jako dependency w Initializr).

---

### Configuration Model

#### [NEW] [SftpProperties.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/config/SftpProperties.java)

Klasa `@ConfigurationProperties("sftp")` z listą użytkowników:

```java
@ConfigurationProperties(prefix = "sftp")
public record SftpProperties(
    List<UserConfig> users,
    Duration pollingInterval  // domyślnie 30s
) {
    public record UserConfig(
        String id,              // np. "user1"
        String host,
        int port,               // domyślnie 22
        String username,
        String password,        // opcjonalne, alternatywa dla privateKey
        String privateKeyPath,  // ścieżka do klucza SSH
        String privateKeyPassphrase,
        String homeDir,         // np. "16000002/"
        String inputDir,        // np. "input"
        String outputDir,       // np. "output"
        boolean allowUnknownKeys // domyślnie false
    ) {}
}
```

---

### Session Factory Layer

#### [NEW] [SftpSessionFactoryProvider.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpSessionFactoryProvider.java)

Serwis tworzący `CachingSessionFactory<SftpClient.DirEntry>` per użytkownik na podstawie konfiguracji. Kluczowe elementy:
- `DefaultSftpSessionFactory` z `privateKey` Resource lub `password`
- `CachingSessionFactory` wrapper dla reuse sesji
- Mapa `Map<String, CachingSessionFactory>` po user ID

---

### Integration Flow — Inbound (pobieranie plików)

#### [NEW] [SftpInboundFlowConfig.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpInboundFlowConfig.java)

Konfiguracja pobierania plików z wielu serwerów z użyciem `DelegatingSessionFactory` + `RotatingServerAdvice`:

- `DelegatingSessionFactory` — runtime wybór sesji na podstawie klucza (user ID)
- `RotatingServerAdvice` — automatyczna rotacja po userach i ich katalogach `{homeDir}/{inputDir}`
- `IntegrationFlow` z `Sftp.inboundAdapter()` i pollerem cyklicznym
- Pliki pobierane do katalogu lokalnego: `./sftp-downloads/{userId}/`
- `SftpPersistentAcceptOnceFileListFilter` z `SimpleMetadataStore` — unikanie ponownego pobierania

---

### File Handler

#### [NEW] [SftpFileHandler.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpFileHandler.java)

`@ServiceActivator` przetwarzający pobrane pliki:
- Loguje informacje o pobranym pliku (nazwa, rozmiar, ścieżka)
- Punkt rozszerzenia do dalszego przetwarzania

---

### Integration Flow — Outbound (placeholder)

#### [NEW] [SftpOutboundFlowConfig.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpOutboundFlowConfig.java)

Gateway do wysyłania plików na SFTP per użytkownik. Konfiguracja:
- `SftpMessageHandler` z `remoteDirectoryExpression` budującym ścieżkę `{homeDir}/{outputDir}`
- Metoda `sendFile(String userId, File file)` jako punkt wejścia

---

### Application Configuration

#### [NEW] [application.yml](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/resources/application.yml)

```yaml
sftp:
  polling-interval: 30s
  users:
    - id: user1
      host: sftp.example.com
      port: 22
      username: user1
      private-key-path: classpath:keys/user1_id_rsa
      home-dir: "16000002/"
      input-dir: "input"
      output-dir: "output"
      allow-unknown-keys: true
    - id: user2
      host: sftp2.example.com
      port: 22
      username: user2
      password: secret
      home-dir: "16000003/"
      input-dir: "input"
      output-dir: "output"
      allow-unknown-keys: true
```

---

## Verification Plan

### Automated Tests

1. **Kompilacja**: `mvn compile` — projekt musi się skompilować bez błędów
2. **Testy konfiguracji**: `mvn test` — test wiązania `SftpProperties` z YAML

### Manual Verification

> [!NOTE]
> Do pełnego przetestowania potrzebny jest dostęp do serwera SFTP. Czy masz testowy serwer SFTP, którego mogę użyć w konfiguracji? Ewentualnie mogę dodać do projektu embedded SFTP server na potrzeby testów integracyjnych (Apache MINA SSHD server).
