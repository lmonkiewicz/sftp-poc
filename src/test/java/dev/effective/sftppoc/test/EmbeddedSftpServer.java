package dev.effective.sftppoc.test;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Embedded SFTP server for integration testing.
 *
 * <p>Uses Apache MINA SSHD to start a local SFTP server on a random port.
 * Files are served from a temporary directory that can be pre-populated
 * with test data.</p>
 */
public class EmbeddedSftpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedSftpServer.class);

    private final SshServer sshServer;
    private final Path rootDir;
    private final int port;

    /**
     * Creates and starts an embedded SFTP server.
     *
     * @param username the username for authentication
     * @param password the password for authentication
     * @throws IOException if the server fails to start
     */
    public EmbeddedSftpServer(String username, String password) throws IOException {
        this.rootDir = Files.createTempDirectory("embedded-sftp-");
        log.info("Embedded SFTP root directory: {}", rootDir);

        this.sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(0); // random available port
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(rootDir.resolve(".hostkey")));

        // Password authenticator
        sshServer.setPasswordAuthenticator((user, pass, session) ->
                username.equals(user) && password.equals(pass));

        // SFTP subsystem
        sshServer.setSubsystemFactories(List.of(new SftpSubsystemFactory()));

        // Virtual filesystem rooted at temp dir
        sshServer.setFileSystemFactory(new VirtualFileSystemFactory(rootDir));

        sshServer.start();
        this.port = sshServer.getPort();
        log.info("Embedded SFTP server started on port {} (user: {})", port, username);
    }

    /**
     * Returns the port the server is listening on.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the root directory of the virtual filesystem.
     */
    public Path getRootDir() {
        return rootDir;
    }

    /**
     * Creates a directory structure relative to the root and returns the path.
     */
    public Path createDirectory(String relativePath) throws IOException {
        Path dir = rootDir.resolve(relativePath);
        Files.createDirectories(dir);
        log.debug("Created directory: {}", dir);
        return dir;
    }

    /**
     * Creates a file with the given content in the specified directory.
     */
    public Path createFile(String relativePath, String content) throws IOException {
        Path file = rootDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        log.debug("Created file: {} ({} bytes)", file, content.length());
        return file;
    }

    /**
     * Checks if a file exists in the virtual filesystem.
     */
    public boolean fileExists(String relativePath) {
        return Files.exists(rootDir.resolve(relativePath));
    }

    /**
     * Reads a file's content from the virtual filesystem.
     */
    public String readFile(String relativePath) throws IOException {
        return Files.readString(rootDir.resolve(relativePath));
    }

    @Override
    public void close() throws Exception {
        if (sshServer != null && sshServer.isOpen()) {
            sshServer.stop(true);
            log.info("Embedded SFTP server stopped");
        }
    }
}
