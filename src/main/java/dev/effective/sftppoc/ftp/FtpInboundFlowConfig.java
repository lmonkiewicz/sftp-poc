package dev.effective.sftppoc.ftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.UserConfig;
import org.apache.commons.net.ftp.FTPFile;
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
import org.springframework.integration.ftp.dsl.Ftp;
import org.springframework.integration.ftp.filters.FtpPersistentAcceptOnceFileListFilter;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.messaging.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configures the inbound FTP integration flow for downloading files
 * from multiple user servers using plain FTP protocol.
 *
 * <p>All beans are safe to create even when no FTP users are configured —
 * the inbound flow uses a no-op pipeline in that case.</p>
 */
@Configuration
public class FtpInboundFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(FtpInboundFlowConfig.class);

    private static final String LOCAL_DOWNLOAD_DIR = "ftp-downloads";

    private final FtpSessionFactoryProvider sessionFactoryProvider;
    private final SftpProperties properties;

    public FtpInboundFlowConfig(FtpSessionFactoryProvider sessionFactoryProvider,
                                 SftpProperties properties) {
        this.sessionFactoryProvider = sessionFactoryProvider;
        this.properties = properties;
    }

    @Bean
    public DelegatingSessionFactory<FTPFile> ftpDelegatingSessionFactory() {
        Map<String, CachingSessionFactory<FTPFile>> allFactories =
                sessionFactoryProvider.getAllSessionFactories();

        Map<Object, SessionFactory<FTPFile>> factoryMap = new LinkedHashMap<>(allFactories);

        List<UserConfig> ftpUsers = properties.usersForProtocol(SftpProperties.Protocol.FTP);
        SessionFactory<FTPFile> defaultFactory = ftpUsers.isEmpty()
                ? null
                : allFactories.get(ftpUsers.getFirst().id());

        DefaultSessionFactoryLocator<FTPFile> locator =
                new DefaultSessionFactoryLocator<>(factoryMap, defaultFactory);

        return new DelegatingSessionFactory<>(locator);
    }

    @Bean
    public IntegrationFlow ftpInboundFlow() {
        List<UserConfig> ftpUsers = properties.usersForProtocol(SftpProperties.Protocol.FTP);

        if (ftpUsers.isEmpty()) {
            log.info("No FTP users configured — FTP inbound flow disabled");
            return f -> f.nullChannel();
        }

        // Build RotatingServerAdvice inline
        List<RotationPolicy.KeyDirectory> keyDirectories = new ArrayList<>();
        for (UserConfig user : ftpUsers) {
            String remoteInputPath = user.remoteInputPath();
            keyDirectories.add(new RotationPolicy.KeyDirectory(user.id(), remoteInputPath));
            log.info("Registered FTP rotation entry: user='{}', remoteDir='{}'", user.id(), remoteInputPath);
        }
        RotatingServerAdvice advice = new RotatingServerAdvice(
                ftpDelegatingSessionFactory(), keyDirectories, true);

        return IntegrationFlow
                .from(Ftp.inboundAdapter(ftpDelegatingSessionFactory())
                                .preserveTimestamp(true)
                                .remoteDirectory(".")
                                .filter(new FtpPersistentAcceptOnceFileListFilter(
                                        new SimpleMetadataStore(), "ftp-inbound"))
                                .localDirectory(new File(LOCAL_DOWNLOAD_DIR))
                                .autoCreateLocalDirectory(true)
                                .localFilenameExpression(
                                        "#remoteDirectory + T(java.io.File).separator + #root"),
                        e -> e.id("ftpInboundAdapter")
                                .autoStartup(properties.enabled())
                                .poller(Pollers.fixedDelay(properties.pollingInterval())
                                        .advice(advice)))
                .handle(this::handleDownloadedFile)
                .get();
    }

    private void handleDownloadedFile(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof File file) {
            log.info("[FTP] Downloaded file: {} (size: {} bytes)", file.getAbsolutePath(), file.length());
        } else {
            log.info("[FTP] Received message: {}", payload);
        }
    }
}
