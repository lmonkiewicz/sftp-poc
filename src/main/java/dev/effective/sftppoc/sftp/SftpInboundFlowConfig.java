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
 * to poll each configured user's SFTP server and input directory in rotation.</p>
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

    /**
     * A DelegatingSessionFactory that selects the session factory at runtime
     * based on the current thread key (set by RotatingServerAdvice).
     */
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

    /**
     * RotatingServerAdvice rotates through all users and their input directories
     * on each poll cycle.
     */
    @Bean
    public RotatingServerAdvice sftpRotatingServerAdvice() {
        List<RotationPolicy.KeyDirectory> keyDirectories = new ArrayList<>();

        for (UserConfig user : properties.usersForProtocol(SftpProperties.Protocol.SFTP)) {
            String remoteInputPath = user.remoteInputPath();
            keyDirectories.add(new RotationPolicy.KeyDirectory(user.id(), remoteInputPath));
            log.info("Registered SFTP rotation entry: user='{}', remoteDir='{}'", user.id(), remoteInputPath);
        }

        return new RotatingServerAdvice(sftpDelegatingSessionFactory(), keyDirectories, true);
    }

    /**
     * Inbound integration flow that downloads files from SFTP servers.
     *
     * <p>The RotatingServerAdvice ensures that polling rotates across
     * all configured user servers and directories. Files are downloaded
     * to local directory organized by remote directory structure.</p>
     */
    @Bean
    public IntegrationFlow sftpInboundFlow() {
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
                                        .advice(sftpRotatingServerAdvice())))
                .handle(this::handleDownloadedFile)
                .get();
    }

    /**
     * Handles each downloaded file message.
     */
    private void handleDownloadedFile(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof File file) {
            log.info("Downloaded file: {} (size: {} bytes)", file.getAbsolutePath(), file.length());
        } else {
            log.info("Received message: {}", payload);
        }
    }
}
