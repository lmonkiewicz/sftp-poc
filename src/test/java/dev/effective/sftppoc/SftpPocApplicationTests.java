package dev.effective.sftppoc;

import dev.effective.sftppoc.config.SftpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the application context loads correctly with the
 * SFTP configuration, without actually connecting to any SFTP server.
 *
 * <p>Tests run with sftp.enabled=false (set in test application.yml)
 * to prevent the inbound adapter from auto-starting and connecting.</p>
 */
@SpringBootTest
class SftpPocApplicationTests {

    @Autowired
    private SftpProperties sftpProperties;

    @Test
    void contextLoads() {
        assertNotNull(sftpProperties);
        assertFalse(sftpProperties.users().isEmpty(),
                "At least one SFTP user should be configured");
    }

    @Test
    void sftpIsDisabledInTestProfile() {
        assertFalse(sftpProperties.enabled(),
                "SFTP should be disabled in test profile");
    }

    @Test
    void sftpPropertiesAreBound() {
        SftpProperties.UserConfig user = sftpProperties.users().getFirst();
        assertEquals("testuser1", user.id());
        assertEquals("localhost", user.host());
        assertEquals(2222, user.port());
        assertEquals("testuser", user.username());
        assertEquals("testpass", user.password());
        assertEquals("test/", user.homeDir());
        assertEquals("input", user.inputDir());
        assertEquals("output", user.outputDir());
        assertTrue(user.allowUnknownKeys());
    }

    @Test
    void remotePathsAreConstructedCorrectly() {
        SftpProperties.UserConfig user = sftpProperties.users().getFirst();
        assertEquals("test/input", user.remoteInputPath());
        assertEquals("test/output", user.remoteOutputPath());
    }
}
