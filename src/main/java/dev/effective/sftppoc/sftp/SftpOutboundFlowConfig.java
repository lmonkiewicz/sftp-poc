package dev.effective.sftppoc.sftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.UserConfig;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.remote.session.DelegatingSessionFactory;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.io.File;

/**
 * Configures the outbound SFTP integration flow for uploading files
 * to user-specific output directories.
 *
 * <p>Uses the shared {@link DelegatingSessionFactory} to route files to the
 * correct SFTP server based on the user. The remote directory is set dynamically
 * via message headers.</p>
 */
@Configuration
public class SftpOutboundFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(SftpOutboundFlowConfig.class);

    private final DelegatingSessionFactory<SftpClient.DirEntry> delegatingSessionFactory;
    private final SftpProperties properties;

    public SftpOutboundFlowConfig(DelegatingSessionFactory<SftpClient.DirEntry> delegatingSessionFactory,
                                   SftpProperties properties) {
        this.delegatingSessionFactory = delegatingSessionFactory;
        this.properties = properties;
    }

    @Bean
    public MessageChannel sftpOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * Outbound flow that handles file uploads to SFTP servers.
     *
     * <p>The remote directory is dynamically resolved from the "remoteDirectory" header.
     * Before sending, the caller must set the thread key on the DelegatingSessionFactory
     * to select the correct user's session factory.</p>
     */
    @Bean
    public IntegrationFlow sftpOutboundFlow() {
        return IntegrationFlow
                .from(sftpOutboundChannel())
                .handle(Sftp.outboundAdapter(delegatingSessionFactory)
                        .remoteDirectoryExpression("headers['remoteDirectory']")
                        .autoCreateDirectory(true))
                .get();
    }

    /**
     * Sends a file to the specified user's output directory on SFTP.
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
        log.info("Sending file '{}' to SFTP for user '{}' -> remote dir: '{}'",
                file.getName(), userId, remoteDir);

        // Set the session factory key for this thread
        delegatingSessionFactory.setThreadKey(userId);
        try {
            Message<File> message = MessageBuilder.withPayload(file)
                    .setHeader("remoteDirectory", remoteDir)
                    .build();

            sftpOutboundChannel().send(message);
        } finally {
            delegatingSessionFactory.clearThreadKey();
        }
    }
}
