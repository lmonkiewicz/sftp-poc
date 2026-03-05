package dev.effective.sftppoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for multi-user SFTP connections.
 *
 * <p>Each user entry defines a separate SFTP server connection with its own
 * credentials, home directory, and input/output paths.</p>
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>
 * sftp:
 *   polling-interval: 30s
 *   users:
 *     - id: user1
 *       host: sftp.example.com
 *       username: user1
 *       private-key-path: classpath:keys/user1_id_rsa
 *       home-dir: "16000002/"
 *       input-dir: "input"
 *       output-dir: "output"
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
     * Configuration for a single SFTP user/server connection.
     */
    public record UserConfig(
            String id,
            String host,
            Integer port,
            String username,
            String password,
            String privateKeyPath,
            String privateKeyPassphrase,
            String homeDir,
            String inputDir,
            String outputDir,
            Boolean allowUnknownKeys
    ) {

        public UserConfig {
            if (port == null) port = 22;
            if (inputDir == null) inputDir = "input";
            if (outputDir == null) outputDir = "output";
            if (allowUnknownKeys == null) allowUnknownKeys = false;
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
