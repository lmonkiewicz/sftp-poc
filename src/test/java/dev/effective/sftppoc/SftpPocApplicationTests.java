package dev.effective.sftppoc;

import dev.effective.sftppoc.config.SftpProperties;
import dev.effective.sftppoc.config.SftpProperties.Protocol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the application context loads correctly with both
 * FTP and SFTP user configurations.
 */
@SpringBootTest
class SftpPocApplicationTests {

    @Autowired
    private SftpProperties sftpProperties;

    @Test
    void contextLoads() {
        assertNotNull(sftpProperties);
        assertEquals(2, sftpProperties.users().size(),
                "Should have 2 users (1 SFTP + 1 FTP)");
    }

    @Test
    void sftpIsDisabledInTestProfile() {
        assertFalse(sftpProperties.enabled());
    }

    @Test
    void sftpUserPropertiesAreBound() {
        SftpProperties.UserConfig user = sftpProperties.usersForProtocol(Protocol.SFTP).getFirst();
        assertEquals("testuser-sftp", user.id());
        assertEquals(Protocol.SFTP, user.protocol());
        assertEquals("localhost", user.host());
        assertEquals(2222, user.port());
        assertEquals("testuser", user.username());
        assertEquals("test/input", user.remoteInputPath());
        assertEquals("test/output", user.remoteOutputPath());
    }

    @Test
    void ftpUserPropertiesAreBound() {
        SftpProperties.UserConfig user = sftpProperties.usersForProtocol(Protocol.FTP).getFirst();
        assertEquals("testuser-ftp", user.id());
        assertEquals(Protocol.FTP, user.protocol());
        assertEquals("localhost", user.host());
        assertEquals(2121, user.port());
        assertEquals("ftptest", user.username());
        assertTrue(user.passiveMode());
        assertEquals("ftptest/in", user.remoteInputPath());
        assertEquals("ftptest/out", user.remoteOutputPath());
    }

    @Test
    void usersForProtocolFiltersCorrectly() {
        List<SftpProperties.UserConfig> sftpUsers = sftpProperties.usersForProtocol(Protocol.SFTP);
        List<SftpProperties.UserConfig> ftpUsers = sftpProperties.usersForProtocol(Protocol.FTP);

        assertEquals(1, sftpUsers.size());
        assertEquals(1, ftpUsers.size());
        assertEquals("testuser-sftp", sftpUsers.getFirst().id());
        assertEquals("testuser-ftp", ftpUsers.getFirst().id());
    }

    @Test
    void defaultPortsAreCorrectPerProtocol() {
        // SFTP defaults to 22, FTP defaults to 21 (but both are overridden in test config)
        SftpProperties.UserConfig sftpUser = sftpProperties.usersForProtocol(Protocol.SFTP).getFirst();
        SftpProperties.UserConfig ftpUser = sftpProperties.usersForProtocol(Protocol.FTP).getFirst();

        assertEquals(2222, sftpUser.port()); // overridden
        assertEquals(2121, ftpUser.port());  // overridden
    }
}
