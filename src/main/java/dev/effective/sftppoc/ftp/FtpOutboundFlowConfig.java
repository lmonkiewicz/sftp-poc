package dev.effective.sftppoc.ftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.UserConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.remote.session.DelegatingSessionFactory;
import org.springframework.integration.ftp.dsl.Ftp;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.io.File;

/**
 * Configures the outbound FTP integration flow for uploading files
 * to user-specific output directories via plain FTP.
 */
@Configuration
public class FtpOutboundFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(FtpOutboundFlowConfig.class);

    private final DelegatingSessionFactory<FTPFile> ftpDelegatingSessionFactory;
    private final SftpProperties properties;

    public FtpOutboundFlowConfig(DelegatingSessionFactory<FTPFile> ftpDelegatingSessionFactory,
                                  SftpProperties properties) {
        this.ftpDelegatingSessionFactory = ftpDelegatingSessionFactory;
        this.properties = properties;
    }

    @Bean
    public MessageChannel ftpOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow ftpOutboundFlow() {
        return IntegrationFlow
                .from(ftpOutboundChannel())
                .handle(Ftp.outboundAdapter(ftpDelegatingSessionFactory)
                        .remoteDirectoryExpression("headers['remoteDirectory']")
                        .autoCreateDirectory(true))
                .get();
    }

    /**
     * Sends a file to the specified FTP user's output directory.
     *
     * @param userId the user ID from configuration
     * @param file   the file to upload
     */
    public void sendFile(String userId, File file) {
        UserConfig user = properties.users().stream()
                .filter(u -> u.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));

        String remoteDir = user.remoteOutputPath();
        log.info("[FTP] Sending file '{}' for user '{}' -> remote dir: '{}'",
                file.getName(), userId, remoteDir);

        ftpDelegatingSessionFactory.setThreadKey(userId);
        try {
            Message<File> message = MessageBuilder.withPayload(file)
                    .setHeader("remoteDirectory", remoteDir)
                    .build();

            ftpOutboundChannel().send(message);
        } finally {
            ftpDelegatingSessionFactory.clearThreadKey();
        }
    }
}
