package dev.effective.sftppoc.ftp;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.test.EmbeddedFtpServer;
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
 * Integration test for FTP inbound and outbound flows using an embedded FTP server.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FtpIntegrationTest {

    private static final String USERNAME = "testftp";
    private static final String PASSWORD = "ftppass123";
    private static final String HOME_DIR = "ftpuser1";
    private static final String INPUT_DIR = "input";
    private static final String OUTPUT_DIR = "output";

    private static EmbeddedFtpServer ftpServer;

    @Autowired
    private SftpProperties properties;

    @Autowired
    private FtpOutboundFlowConfig outboundFlowConfig;

    @Autowired(required = false)
    private SourcePollingChannelAdapter ftpInboundAdapter;

    @DynamicPropertySource
    static void configureFtpProperties(DynamicPropertyRegistry registry) throws Exception {
        if (ftpServer == null) {
            ftpServer = new EmbeddedFtpServer(USERNAME, PASSWORD);
            ftpServer.createDirectory(HOME_DIR + "/" + INPUT_DIR);
            ftpServer.createDirectory(HOME_DIR + "/" + OUTPUT_DIR);
            ftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/data1.txt",
                    "FTP test data file 1");
            ftpServer.createFile(HOME_DIR + "/" + INPUT_DIR + "/data2.csv",
                    "a,b,c\n1,2,3");
        }

        registry.add("sftp.enabled", () -> false);
        registry.add("sftp.polling-interval", () -> "1s");

        // Configure FTP user pointing at embedded server
        registry.add("sftp.users[0].id", () -> "ftp-test-user");
        registry.add("sftp.users[0].protocol", () -> "FTP");
        registry.add("sftp.users[0].host", () -> "localhost");
        registry.add("sftp.users[0].port", () -> String.valueOf(ftpServer.getPort()));
        registry.add("sftp.users[0].username", () -> USERNAME);
        registry.add("sftp.users[0].password", () -> PASSWORD);
        registry.add("sftp.users[0].home-dir", () -> HOME_DIR + "/");
        registry.add("sftp.users[0].input-dir", () -> INPUT_DIR);
        registry.add("sftp.users[0].output-dir", () -> OUTPUT_DIR);
        registry.add("sftp.users[0].passive-mode", () -> "true");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (ftpServer != null) {
            ftpServer.close();
        }
        Path downloadDir = Path.of("ftp-downloads");
        if (Files.exists(downloadDir)) {
            Files.walk(downloadDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    @Order(1)
    void configurationPointsToEmbeddedFtpServer() {
        SftpProperties.UserConfig user = properties.usersForProtocol(SftpProperties.Protocol.FTP).getFirst();
        assertEquals("ftp-test-user", user.id());
        assertEquals("localhost", user.host());
        assertEquals(ftpServer.getPort(), user.port());
        assertEquals(SftpProperties.Protocol.FTP, user.protocol());
    }

    @Test
    @Order(2)
    void embeddedFtpServerHasTestFiles() {
        assertTrue(ftpServer.fileExists(HOME_DIR + "/" + INPUT_DIR + "/data1.txt"));
        assertTrue(ftpServer.fileExists(HOME_DIR + "/" + INPUT_DIR + "/data2.csv"));
    }

    @Test
    @Order(3)
    void ftpInboundAdapterDownloadsFiles() throws Exception {
        assertNotNull(ftpInboundAdapter, "FTP Inbound adapter should be wired");

        ftpInboundAdapter.start();
        Thread.sleep(5000);
        ftpInboundAdapter.stop();

        Path downloadDir = Path.of("ftp-downloads");
        assertTrue(Files.exists(downloadDir), "FTP download directory should exist");

        long fileCount = Files.walk(downloadDir)
                .filter(Files::isRegularFile)
                .peek(f -> System.out.println("  FTP Downloaded: " + f))
                .count();

        assertTrue(fileCount >= 2,
                "Expected at least 2 downloaded FTP files, found: " + fileCount);
    }

    @Test
    @Order(4)
    void ftpOutboundUploadsFile() throws Exception {
        Path tempFile = Files.createTempFile("ftp-upload-", ".txt");
        Files.writeString(tempFile, "Uploaded via FTP outbound flow");

        outboundFlowConfig.sendFile("ftp-test-user", tempFile.toFile());

        String uploadedPath = HOME_DIR + "/" + OUTPUT_DIR + "/" + tempFile.getFileName();
        assertTrue(ftpServer.fileExists(uploadedPath),
                "File should exist on FTP server at: " + uploadedPath);

        String content = ftpServer.readFile(uploadedPath);
        assertEquals("Uploaded via FTP outbound flow", content);

        Files.deleteIfExists(tempFile);
    }
}
