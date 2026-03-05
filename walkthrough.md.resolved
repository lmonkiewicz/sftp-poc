# Walkthrough: SFTP PoC — Spring Boot 4.x + Spring Integration

## Summary

Zbudowana aplikacja Spring Boot 4.0.3 z Spring Integration 7.0 do pobierania plików z serwerów SFTP z obsługą wielu użytkowników. Każdy użytkownik ma własny adres SFTP, folder domowy, ścieżki input/output, i osobne uwierzytelnianie.

## Architecture

```mermaid
graph TD
    A[application.yml<br>Multi-user config] --> B[SftpProperties<br>@ConfigurationProperties]
    B --> C[SftpSessionFactoryProvider<br>CachingSessionFactory per user]
    C --> D[DelegatingSessionFactory<br>Runtime session selection]
    D --> E[SftpInboundFlowConfig<br>RotatingServerAdvice + Poller]
    D --> F[SftpOutboundFlowConfig<br>Upload to output dirs]
    E --> G[Downloaded files<br>sftp-downloads/]
```

## Created Files

| File | Purpose |
|------|---------|
| [pom.xml](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/pom.xml) | Spring Boot 4.0.3, Java 21, spring-integration-sftp |
| [SftpProperties.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/config/SftpProperties.java) | Record-based config: users, host, auth, paths |
| [SftpSessionFactoryProvider.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpSessionFactoryProvider.java) | Tworzy CachingSessionFactory per user |
| [SftpInboundFlowConfig.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpInboundFlowConfig.java) | DelegatingSessionFactory + RotatingServerAdvice |
| [SftpOutboundFlowConfig.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpOutboundFlowConfig.java) | Outbound upload flow via sendFile() |
| [SftpPocApplication.java](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/SftpPocApplication.java) | Main class + @EnableIntegration |
| [application.yml](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/resources/application.yml) | Example multi-user config |

## Key Design Decisions

1. **`DelegatingSessionFactory` + `RotatingServerAdvice`** — zamiast tworzenia osobnego `IntegrationFlow` per user, jeden flow rotuje po wszystkich użytkownikach z fairness=true
2. **SSH keys zamiast SSLBundles** — SFTP działa na SSH, nie TLS. `DefaultSftpSessionFactory.setPrivateKey()` obsługuje klucze SSH natywnie
3. **`sftp.enabled` property** — kontroluje `autoStartup` inbound adaptera, pozwala wyłączyć w testach
4. **Record-based config** — [SftpProperties](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/config/SftpProperties.java#28-93) i [UserConfig](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/config/SftpProperties.java#50-92) jako Java records z default values w compact constructor

## Test Results

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [contextLoads](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/test/java/dev/effective/sftppoc/SftpPocApplicationTests.java#23-29) — kontekst Spring startuje poprawnie
- [sftpIsDisabledInTestProfile](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/test/java/dev/effective/sftppoc/SftpPocApplicationTests.java#30-35) — weryfikuje że `sftp.enabled=false` w testach
- [sftpPropertiesAreBound](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/test/java/dev/effective/sftppoc/SftpPocApplicationTests.java#36-49) — powiązanie properties z YAML
- [remotePathsAreConstructedCorrectly](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/test/java/dev/effective/sftppoc/SftpPocApplicationTests.java#50-56) — `test/input` i `test/output`

## Next Steps

- Skonfigurować realne adresy SFTP i klucze SSH w [application.yml](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/test/resources/application.yml)
- Rozbudować [sendFile()](file:///Users/lukaszmonkiewicz/git/effectivedev/code/sftp-poc/src/main/java/dev/effective/sftppoc/sftp/SftpOutboundFlowConfig.java#64-92) w outbound flow o obsługę wielu serwerów
- Opcjonalnie dodać embedded Apache MINA SSHD do testów integracyjnych
