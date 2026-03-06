package dev.effective.sftppoc.test;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Embedded FTP server for integration testing.
 *
 * <p>Uses Apache FtpServer to start a local FTP server on a random port.
 * Files are served from a temporary directory.</p>
 */
public class EmbeddedFtpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedFtpServer.class);

    private final FtpServer ftpServer;
    private final Path rootDir;
    private final int port;

    /**
     * Creates and starts an embedded FTP server.
     *
     * @param username the username for authentication
     * @param password the password for authentication
     * @throws IOException  if directory creation fails
     * @throws FtpException if the server fails to start
     */
    public EmbeddedFtpServer(String username, String password) throws IOException, FtpException {
        this.rootDir = Files.createTempDirectory("embedded-ftp-");
        log.info("Embedded FTP root directory: {}", rootDir);

        FtpServerFactory serverFactory = new FtpServerFactory();

        // Listener on random port
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(0); // random port
        serverFactory.addListener("default", listenerFactory.createListener());

        // User manager
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        var userManager = userManagerFactory.createUserManager();

        BaseUser user = new BaseUser();
        user.setName(username);
        user.setPassword(password);
        user.setHomeDirectory(rootDir.toString());
        user.setAuthorities(List.of(new WritePermission()));
        userManager.save(user);

        serverFactory.setUserManager(userManager);

        this.ftpServer = serverFactory.createServer();
        ftpServer.start();

        // Get the actual port
        this.port = serverFactory.getListener("default").getPort();
        log.info("Embedded FTP server started on port {} (user: {})", port, username);
    }

    public int getPort() {
        return port;
    }

    public Path getRootDir() {
        return rootDir;
    }

    public Path createDirectory(String relativePath) throws IOException {
        Path dir = rootDir.resolve(relativePath);
        Files.createDirectories(dir);
        return dir;
    }

    public Path createFile(String relativePath, String content) throws IOException {
        Path file = rootDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    public boolean fileExists(String relativePath) {
        return Files.exists(rootDir.resolve(relativePath));
    }

    public String readFile(String relativePath) throws IOException {
        return Files.readString(rootDir.resolve(relativePath));
    }

    @Override
    public void close() throws Exception {
        if (ftpServer != null && !ftpServer.isStopped()) {
            ftpServer.stop();
            log.info("Embedded FTP server stopped");
        }
    }
}
