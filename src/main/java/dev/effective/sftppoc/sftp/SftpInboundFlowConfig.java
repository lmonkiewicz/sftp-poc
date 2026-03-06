package dev.effective.sftppoc.sftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.UserConfig;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.remote.aop.RotatingServerAdvice;
import org.springframework.integration.file.remote.aop.RotationPolicy;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.DefaultSessionFactoryLocator;
import org.springframework.integration.file.remote.session.DelegatingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.filters.SftpPersistentAcceptOnceFileListFilter;
import org.springframework.messaging.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configures the inbound SFTP integration flow for downloading files
 * from multiple user servers.
 *
 * <p>Uses {@link DelegatingSessionFactory} with {@link RotatingServerAdvice}
 * to poll each configured SFTP user's server in rotation.</p>
 *
 * <p>All beans are safe to create even when no SFTP users are configured —
 * the inbound flow uses a no-op pipeline in that case.</p>
 */
@Configuration
public class SftpInboundFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(SftpInboundFlowConfig.class);

    private static final String LOCAL_DOWNLOAD_DIR = "sftp-downloads";

    private final SftpSessionFactoryProvider sessionFactoryProvider;
    private final SftpProperties properties;

    public SftpInboundFlowConfig(SftpSessionFactoryProvider sessionFactoryProvider,
                                  SftpProperties properties) {
        this.sessionFactoryProvider = sessionFactoryProvider;
        this.properties = properties;
    }

    @Bean
    public DelegatingSessionFactory<SftpClient.DirEntry> sftpDelegatingSessionFactory() {
        Map<String, CachingSessionFactory<SftpClient.DirEntry>> allFactories =
                sessionFactoryProvider.getAllSessionFactories();

        Map<Object, SessionFactory<SftpClient.DirEntry>> factoryMap = new LinkedHashMap<>(allFactories);

        List<UserConfig> sftpUsers = properties.usersForProtocol(SftpProperties.Protocol.SFTP);
        SessionFactory<SftpClient.DirEntry> defaultFactory = sftpUsers.isEmpty()
                ? null
                : allFactories.get(sftpUsers.getFirst().id());

        DefaultSessionFactoryLocator<SftpClient.DirEntry> locator =
                new DefaultSessionFactoryLocator<>(factoryMap, defaultFactory);

        return new DelegatingSessionFactory<>(locator);
    }

    @Bean
    public IntegrationFlow sftpInboundFlow() {
        List<UserConfig> sftpUsers = properties.usersForProtocol(SftpProperties.Protocol.SFTP);

        if (sftpUsers.isEmpty()) {
            log.info("No SFTP users configured — SFTP inbound flow disabled");
            return f -> f.nullChannel();
        }

        // Build RotatingServerAdvice inline
        List<RotationPolicy.KeyDirectory> keyDirectories = new ArrayList<>();
        for (UserConfig user : sftpUsers) {
            String remoteInputPath = user.remoteInputPath();
            keyDirectories.add(new RotationPolicy.KeyDirectory(user.id(), remoteInputPath));
            log.info("Registered SFTP rotation entry: user='{}', remoteDir='{}'", user.id(), remoteInputPath);
        }
        RotatingServerAdvice advice = new RotatingServerAdvice(
                sftpDelegatingSessionFactory(), keyDirectories, true);

        return IntegrationFlow
                .from(Sftp.inboundAdapter(sftpDelegatingSessionFactory())
                                .preserveTimestamp(true)
                                .remoteDirectory(".")
                                .filter(new SftpPersistentAcceptOnceFileListFilter(
                                        new SimpleMetadataStore(), "sftp-inbound"))
                                .localDirectory(new File(LOCAL_DOWNLOAD_DIR))
                                .autoCreateLocalDirectory(true)
                                .localFilenameExpression(
                                        "#remoteDirectory + T(java.io.File).separator + #root"),
                        e -> e.id("sftpInboundAdapter")
                                .autoStartup(properties.enabled())
                                .poller(Pollers.fixedDelay(properties.pollingInterval())
                                        .advice(advice)))
                .handle(this::handleDownloadedFile)
                .get();
    }

    private void handleDownloadedFile(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof File file) {
            log.info("[SFTP] Downloaded file: {} (size: {} bytes)", file.getAbsolutePath(), file.length());
        } else {
            log.info("[SFTP] Received message: {}", payload);
        }
    }
}
