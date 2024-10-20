package org.cy.redisclone;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RedisCloneIntegrationTest {

    private RedisServer server;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;
    private ExecutorService executorService;
    private int port;

    @BeforeAll
    void setUp() throws IOException {
        // Find a random available port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // Start the server in a separate thread
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            RedisLikeService service = new RedisLikeService();
            server = new RedisServer(port, service);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Wait for the server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create a Lettuce client
        redisClient = RedisClient.create("redis://localhost:" + port);
        connection = redisClient.connect();
        syncCommands = connection.sync();
    }

    @AfterAll
    void tearDown() {
        connection.close();
        redisClient.shutdown();
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testSetAndGet() {
        syncCommands.set("testKey", "testValue");
        String value = syncCommands.get("testKey");
        assertEquals("testValue", value);
    }

    @Test
    void testDel() {
        syncCommands.set("deleteMe", "toBeDeleted");
        Long deletedCount = syncCommands.del("deleteMe");
        assertEquals(1L, deletedCount);
        assertNull(syncCommands.get("deleteMe"));
    }

    @Test
    void testKeys() {
        syncCommands.set("key1", "value1");
        syncCommands.set("key2", "value2");
        syncCommands.set("anotherKey", "value3");

        List<String> keys = syncCommands.keys("key*");
        assertEquals(2, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
    }

    @Test
    void testPing() {
        String pong = syncCommands.ping();
        assertEquals("PONG", pong);
    }

    @Test
    void testInfo() {
        String info = syncCommands.info();
        assertNotNull(info);
        assertTrue(info.contains("redis_version"));
        assertTrue(info.contains("connected_clients"));
    }
}
