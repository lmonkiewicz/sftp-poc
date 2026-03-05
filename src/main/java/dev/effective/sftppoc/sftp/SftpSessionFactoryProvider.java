package dev.effective.sftppoc.sftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.UserConfig;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides per-user SFTP session factories.
 *
 * <p>For each user in the configuration, this component creates a
 * {@link DefaultSftpSessionFactory} with the appropriate credentials
 * (password or SSH private key) and wraps it in a {@link CachingSessionFactory}
 * for session reuse.</p>
 */
@Component
public class SftpSessionFactoryProvider {

    private static final Logger log = LoggerFactory.getLogger(SftpSessionFactoryProvider.class);

    private final Map<String, CachingSessionFactory<SftpClient.DirEntry>> sessionFactories;
    private final SftpProperties properties;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    public SftpSessionFactoryProvider(SftpProperties properties) {
        this.properties = properties;
        this.sessionFactories = properties.users().stream()
                .collect(Collectors.toMap(
                        UserConfig::id,
                        this::createCachingSessionFactory,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        log.info("Initialized SFTP session factories for {} users: {}",
                sessionFactories.size(), sessionFactories.keySet());
    }

    /**
     * Returns the cached session factory for a specific user ID.
     */
    public CachingSessionFactory<SftpClient.DirEntry> getSessionFactory(String userId) {
        CachingSessionFactory<SftpClient.DirEntry> factory = sessionFactories.get(userId);
        if (factory == null) {
            throw new IllegalArgumentException("No SFTP session factory configured for user: " + userId);
        }
        return factory;
    }

    /**
     * Returns all configured session factories keyed by user ID.
     */
    public Map<String, CachingSessionFactory<SftpClient.DirEntry>> getAllSessionFactories() {
        return Map.copyOf(sessionFactories);
    }

    /**
     * Returns the SFTP properties for reference.
     */
    public SftpProperties getProperties() {
        return properties;
    }

    private CachingSessionFactory<SftpClient.DirEntry> createCachingSessionFactory(UserConfig user) {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(user.host());
        factory.setPort(user.port());
        factory.setUser(user.username());
        factory.setAllowUnknownKeys(user.allowUnknownKeys());

        if (user.privateKeyPath() != null && !user.privateKeyPath().isBlank()) {
            factory.setPrivateKey(resourceLoader.getResource(user.privateKeyPath()));
            if (user.privateKeyPassphrase() != null) {
                factory.setPrivateKeyPassphrase(user.privateKeyPassphrase());
            }
            log.info("Configured SFTP session for user '{}' with private key auth -> {}:{}",
                    user.id(), user.host(), user.port());
        } else if (user.password() != null && !user.password().isBlank()) {
            factory.setPassword(user.password());
            log.info("Configured SFTP session for user '{}' with password auth -> {}:{}",
                    user.id(), user.host(), user.port());
        } else {
            throw new IllegalStateException(
                    "User '%s' must have either privateKeyPath or password configured".formatted(user.id()));
        }

        CachingSessionFactory<SftpClient.DirEntry> cachingFactory = new CachingSessionFactory<>(factory);
        cachingFactory.setPoolSize(10);
        cachingFactory.setSessionWaitTimeout(30_000);
        return cachingFactory;
    }
}
