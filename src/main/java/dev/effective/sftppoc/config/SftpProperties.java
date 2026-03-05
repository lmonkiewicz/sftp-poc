package dev.effective.sftppoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for multi-user file transfer connections (FTP and SFTP).
 *
 * <p>Each user entry defines a separate server connection with its own
 * protocol, credentials, home directory, and input/output paths.</p>
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>
 * sftp:
 *   polling-interval: 30s
 *   users:
 *     - id: user1
 *       protocol: SFTP
 *       host: sftp.example.com
 *       username: user1
 *       private-key-path: classpath:keys/user1_id_rsa
 *       home-dir: "16000002/"
 *     - id: user2
 *       protocol: FTP
 *       host: ftp.example.com
 *       username: user2
 *       password: secret
 *       home-dir: "16000003/"
 * </pre>
 */
@ConfigurationProperties(prefix = "sftp")
public record SftpProperties(
        Boolean enabled,
        Duration pollingInterval,
        List<UserConfig> users
) {

    public SftpProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (pollingInterval == null) {
            pollingInterval = Duration.ofSeconds(30);
        }
        if (users == null) {
            users = List.of();
        }
    }

    /**
     * Returns only users configured for the given protocol.
     */
    public List<UserConfig> usersForProtocol(Protocol protocol) {
        return users.stream()
                .filter(u -> u.protocol() == protocol)
                .toList();
    }

    /**
     * Supported file transfer protocols.
     */
    public enum Protocol {
        FTP, SFTP
    }

    /**
     * Configuration for a single user/server connection.
     */
    public record UserConfig(
            String id,
            Protocol protocol,
            String host,
            Integer port,
            String username,
            String password,
            String privateKeyPath,
            String privateKeyPassphrase,
            String homeDir,
            String inputDir,
            String outputDir,
            Boolean allowUnknownKeys,
            Boolean passiveMode,
            Boolean useSsl
    ) {

        public UserConfig {
            if (protocol == null) protocol = Protocol.SFTP;
            if (port == null) port = (protocol == Protocol.FTP) ? 21 : 22;
            if (inputDir == null) inputDir = "input";
            if (outputDir == null) outputDir = "output";
            if (allowUnknownKeys == null) allowUnknownKeys = false;
            if (passiveMode == null) passiveMode = true;
            if (useSsl == null) useSsl = false;
        }

        /**
         * Returns the full remote path for reading files: {homeDir}/{inputDir}
         */
        public String remoteInputPath() {
            return normalizePath(homeDir) + inputDir;
        }

        /**
         * Returns the full remote path for writing files: {homeDir}/{outputDir}
         */
        public String remoteOutputPath() {
            return normalizePath(homeDir) + outputDir;
        }

        private String normalizePath(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            return path.endsWith("/") ? path : path + "/";
        }
    }
}
