package redis.embedded;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.UUID;

public class RedisServer {

    private static enum RedisServerEnum {
        WINDOWS("redis-server.exe"),
        LINUX("redis-server"),
        MACOSX("redis-server");

        private final String executableName;

        private RedisServerEnum(String executableName) {
            this.executableName = executableName;
        }

        public static RedisServerEnum getOsDependentRedisServerEnum() {
            String osName = System.getProperty("os.name").toLowerCase();

            if (osName.contains("win")) {
                return WINDOWS;
            } else if (osName.equals("linux")) {
                return LINUX;
            } else if ("mac os x".equals(osName)) {
                return MACOSX;
            } else {
                throw new RuntimeException("Unsupported os/architecture...: " + osName);
            }
        }
    }

    private static final String REDIS_READY_PATTERN = ".*The server is now ready to accept connections on port.*";
    private final String LATEST_REDIS_VERSION = "2.8.9";

    private final File command;
    private final int port;
    private final String password;
    private final String version;

    private volatile boolean active = false;
    private Process redisProcess;

    public RedisServer() throws IOException, URISyntaxException {
        this(findFreePort());
    }

    public RedisServer(String version) throws IOException, URISyntaxException {
        this(version, findFreePort());
    }

    public RedisServer(String version, int port) throws IOException, URISyntaxException {
        this(version, port, null);
    }

    public RedisServer(int port) throws IOException, URISyntaxException {
        this(null, port, null);
    }

    public RedisServer(int port, String password) throws IOException, URISyntaxException {
        this(null, port, password);
    }

    public RedisServer(String version, int port, String password) throws IOException, URISyntaxException {
        this.version = (version != null) ? version : LATEST_REDIS_VERSION;
        this.port = port;
        this.password = password;
        this.command = extractExecutableFromJar(RedisServerEnum.getOsDependentRedisServerEnum());
    }

    private File extractExecutableFromJar(RedisServerEnum redisServerEnum) throws IOException, URISyntaxException {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
        tmpDir.deleteOnExit();
        tmpDir.mkdirs();

        String redisExecutablePath = "redis" + File.separator + version + File.separator + redisServerEnum.name().toLowerCase() + File.separator + redisServerEnum.executableName;
        //URL redisExecutableUrl = RedisServer.class.getClassLoader().getResource(redisExecutablePath);
        InputStream redisExecutableIs = RedisServer.class.getClassLoader().getResourceAsStream(redisExecutablePath);
        File redisExecutableFile = new File(tmpDir, redisServerEnum.executableName);
        //redisExecutableFile.createNewFile();

        //copyFile(redisExecutableIs, redisExecutableFile);

        long num = Files.copy(redisExecutableIs, redisExecutableFile.getAbsoluteFile().toPath());
        //LOG.

        redisExecutableFile.setExecutable(true);
        redisExecutableFile.deleteOnExit();

        return redisExecutableFile;
    }


    public int getPort() {
        return port;
    }

    public String getVersion() {
        return version;
    }

    public String getPassword(){
        return password;
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void start() throws IOException {
        if (active) {
            throw new RuntimeException("This redis server instance is already running...");
        }

        redisProcess = createRedisProcessBuilder().start();
        awaitRedisServerReady();
        active = true;
    }

    private void awaitRedisServerReady() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(redisProcess.getInputStream()));
        try {
            String outputLine;
            do {
                outputLine = reader.readLine();
            } while (outputLine != null && !outputLine.matches(REDIS_READY_PATTERN));
        } finally {
            reader.close();
        }
    }

    private ProcessBuilder createRedisProcessBuilder() {

        ProcessBuilder pb;

        if(password == null || password.isEmpty()){
            pb = new ProcessBuilder(command.getAbsolutePath(), "--port", Integer.toString(port));
        } else {
            pb = new ProcessBuilder(command.getAbsolutePath(), "--port", Integer.toString(port), "--requirepass", password);
        }

        pb.directory(command.getParentFile());

        return pb;
    }

    public synchronized void stop() throws InterruptedException {
        if (active) {
            redisProcess.destroy();
            redisProcess.waitFor();
            active = false;
        }
    }

    private static int findFreePort() throws IOException {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        server.close();
        return port;
    }

    private static void copyFile(File sourceFile, File destFile) throws IOException {
        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }
}
