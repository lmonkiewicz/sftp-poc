package dev.effective.sftppoc.sftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.test.EmbeddedSftpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SFTP inbound and outbound flows using an embedded SFTP server.
 *
 * <p>Starts an embedded Apache MINA SSHD server, places test files in its
 * virtual filesystem, and verifies that the Spring Integration SFTP inbound
 * adapter downloads them correctly.</p>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SftpIntegrationTest {

    private static final String USERNAME = "testsftp";
    private static final String PASSWORD = "testpass123";
    private static final String HOME_DIR = "user1";
    private static final String INPUT_DIR = "input";
    private static final String OUTPUT_DIR = "output";

    private static EmbeddedSftpServer sftpServer;

    @Autowired
    private SftpProperties properties;

    @Autowired
    private SftpOutboundFlowConfig outboundFlowConfig;

    @Autowired(required = false)
    private SourcePollingChannelAdapter sftpInboundAdapter;

    @BeforeAll
    static void startServer() throws IOException {
        sftpServer = new EmbeddedSftpServer(USERNAME, PASSWORD);

        // Create directory structure: user1/input/ and user1/output/
        sftpServer.createDirectory(HOME_DIR + "/" + INPUT_DIR);
        sftpServer.createDirectory(HOME_DIR + "/" + OUTPUT_DIR);

        // Place test files
        sftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/file1.txt",
                "Content of file 1 from SFTP");
        sftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/file2.csv",
                "col1,col2\nval1,val2");
        sftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/file3.xml",
                "<root><item>SFTP test data</item></root>");
    }

    @DynamicPropertySource
    static void configureSftpProperties(DynamicPropertyRegistry registry) throws IOException {
        // This is called before @BeforeAll, so we need to create server here too
        if (sftpServer == null) {
            sftpServer = new EmbeddedSftpServer(USERNAME, PASSWORD);
            sftpServer.createDirectory(HOME_DIR + "/" + INPUT_DIR);
            sftpServer.createDirectory(HOME_DIR + "/" + OUTPUT_DIR);
            sftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/file1.txt",
                    "Content of file 1 from SFTP");
            sftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/file2.csv",
                    "col1,col2\nval1,val2");
            sftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/file3.xml",
                    "<root><item>SFTP test data</item></root>");
        }

        registry.add("sftp.enabled", () -> false); // don't auto-start poller
        registry.add("sftp.polling-interval", () -> "1s");

        // Configure SFTP user pointing at embedded server
        registry.add("sftp.users[0].id", () -> "sftp-test-user");
        registry.add("sftp.users[0].protocol", () -> "SFTP");
        registry.add("sftp.users[0].host", () -> "localhost");
        registry.add("sftp.users[0].port", () -> String.valueOf(sftpServer.getPort()));
        registry.add("sftp.users[0].username", () -> USERNAME);
        registry.add("sftp.users[0].password", () -> PASSWORD);
        registry.add("sftp.users[0].home-dir", () -> HOME_DIR + "/");
        registry.add("sftp.users[0].input-dir", () -> INPUT_DIR);
        registry.add("sftp.users[0].output-dir", () -> OUTPUT_DIR);
        registry.add("sftp.users[0].allow-unknown-keys", () -> "true");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (sftpServer != null) {
            sftpServer.close();
        }
        // Cleanup local download directory
        Path downloadDir = Path.of("sftp-downloads");
        if (Files.exists(downloadDir)) {
            Files.walk(downloadDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    @Order(1)
    void configurationPointsToEmbeddedServer() {
        assertFalse(properties.users().isEmpty());
        SftpProperties.UserConfig user = properties.usersForProtocol(SftpProperties.Protocol.SFTP).getFirst();
        assertEquals("sftp-test-user", user.id());
        assertEquals("localhost", user.host());
        assertEquals(sftpServer.getPort(), user.port());
        assertEquals(HOME_DIR + "/" + INPUT_DIR, user.remoteInputPath());
    }

    @Test
    @Order(2)
    void embeddedServerHasTestFiles() {
        assertTrue(sftpServer.fileExists(HOME_DIR + "/" + INPUT_DIR + "/file1.txt"));
        assertTrue(sftpServer.fileExists(HOME_DIR + "/" + INPUT_DIR + "/file2.csv"));
        assertTrue(sftpServer.fileExists(HOME_DIR + "/" + INPUT_DIR + "/file3.xml"));
    }

    @Test
    @Order(3)
    void sftpInboundAdapterDownloadsFiles() throws Exception {
        assertNotNull(sftpInboundAdapter, "Inbound adapter should be wired");

        // Manually start the adapter and trigger a poll
        sftpInboundAdapter.start();

        // Wait for files to be downloaded (poll + sync)
        Thread.sleep(5000);

        sftpInboundAdapter.stop();

        // Verify files were downloaded to local directory
        Path downloadDir = Path.of("sftp-downloads");
        assertTrue(Files.exists(downloadDir), "Download directory should exist");

        // Files are stored with remoteDirectory path prefix
        // Look recursively for downloaded files
        long fileCount = Files.walk(downloadDir)
                .filter(Files::isRegularFile)
                .peek(f -> System.out.println("  Downloaded: " + f))
                .count();

        assertTrue(fileCount >= 3,
                "Expected at least 3 downloaded files, found: " + fileCount);
    }

    @Test
    @Order(4)
    void sftpOutboundUploadsFile() throws Exception {
        // Create a temporary file to upload
        Path tempFile = Files.createTempFile("upload-test-", ".txt");
        Files.writeString(tempFile, "Uploaded via SFTP outbound flow");

        // Upload the file
        outboundFlowConfig.sendFile("sftp-test-user", tempFile.toFile());

        // Verify the file appeared on the embedded SFTP server
        String uploadedPath = HOME_DIR + "/" + OUTPUT_DIR + "/" + tempFile.getFileName();
        assertTrue(sftpServer.fileExists(uploadedPath),
                "File should exist on SFTP server at: " + uploadedPath);

        String content = sftpServer.readFile(uploadedPath);
        assertEquals("Uploaded via SFTP outbound flow", content);

        Files.deleteIfExists(tempFile);
    }
}
