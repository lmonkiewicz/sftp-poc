package dev.effective.sftppoc.ftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.UserConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides per-user FTP session factories.
 *
 * <p>For each user configured with protocol=FTP, this component creates a
 * {@link DefaultFtpSessionFactory} and wraps it in a {@link CachingSessionFactory}
 * for session reuse.</p>
 */
@Component
public class FtpSessionFactoryProvider {

    private static final Logger log = LoggerFactory.getLogger(FtpSessionFactoryProvider.class);

    private final Map<String, CachingSessionFactory<FTPFile>> sessionFactories;
    private final SftpProperties properties;

    public FtpSessionFactoryProvider(SftpProperties properties) {
        this.properties = properties;
        this.sessionFactories = properties.usersForProtocol(SftpProperties.Protocol.FTP).stream()
                .collect(Collectors.toMap(
                        UserConfig::id,
                        this::createCachingSessionFactory,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        if (!sessionFactories.isEmpty()) {
            log.info("Initialized FTP session factories for {} users: {}",
                    sessionFactories.size(), sessionFactories.keySet());
        }
    }

    public CachingSessionFactory<FTPFile> getSessionFactory(String userId) {
        CachingSessionFactory<FTPFile> factory = sessionFactories.get(userId);
        if (factory == null) {
            throw new IllegalArgumentException("No FTP session factory configured for user: " + userId);
        }
        return factory;
    }

    public Map<String, CachingSessionFactory<FTPFile>> getAllSessionFactories() {
        return Map.copyOf(sessionFactories);
    }

    public boolean hasUsers() {
        return !sessionFactories.isEmpty();
    }

    private CachingSessionFactory<FTPFile> createCachingSessionFactory(UserConfig user) {
        DefaultFtpSessionFactory factory = new DefaultFtpSessionFactory();
        factory.setHost(user.host());
        factory.setPort(user.port());
        factory.setUsername(user.username());

        if (user.password() != null && !user.password().isBlank()) {
            factory.setPassword(user.password());
        }

        // FTP-specific settings
        if (user.passiveMode()) {
            factory.setClientMode(org.apache.commons.net.ftp.FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE);
        }

        log.info("Configured FTP session for user '{}' -> {}:{} (passive={}, ssl={})",
                user.id(), user.host(), user.port(), user.passiveMode(), user.useSsl());

        CachingSessionFactory<FTPFile> cachingFactory = new CachingSessionFactory<>(factory);
        cachingFactory.setPoolSize(10);
        cachingFactory.setSessionWaitTimeout(30_000);
        return cachingFactory;
    }
}
