package org.cy.redisclone;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class RedisServer {
    private static final String VERSION = "1.0.0";
    private static long startTime;
    private static int connectedClients = 0;
    private final int port;
    private final RedisLikeService service;

    public RedisServer(int port, RedisLikeService service) {
        this.port = port;
        this.service = service;
        startTime = System.currentTimeMillis();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, service)).start();
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final RedisLikeService service;

        public ClientHandler(Socket socket, RedisLikeService service) {
            this.clientSocket = socket;
            this.service = service;
            connectedClients++;
        }

        @Override
        public void run() {
            System.out.println("New client connected: " + clientSocket.getInetAddress());
            try (
                    InputStream in = clientSocket.getInputStream();
                    OutputStream out = clientSocket.getOutputStream()
            ) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                StringBuilder commandBuilder = new StringBuilder();

                while ((bytesRead = in.read(buffer)) != -1) {
                    String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    commandBuilder.append(received);

                    while (true) {
                        String command = commandBuilder.toString();
                        try {
                            List<Object> parsed = RESPHandler.parseRequest(command);
                            String response = executeCommand(parsed);
                            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                            out.write(responseBytes);
                            out.flush();
                            commandBuilder = new StringBuilder();
                            break;
                        } catch (IllegalArgumentException e) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                // Silent exception handling
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Silent exception handling
                }
                connectedClients--;
            }
        }

        private String executeCommand(List<Object> parsed) {
            if (parsed.isEmpty() || !(parsed.get(0) instanceof List)) {
                return RESPHandler.formatResponse(new Error("Invalid command format"));
            }

            List<String> args = (List<String>) parsed.get(0);
            if (args.isEmpty()) {
                return RESPHandler.formatResponse(new Error("Empty command"));
            }

            String cmd = args.get(0).toUpperCase();

            switch (cmd) {
                case "GET":
                    if (args.size() != 2) {
                        return RESPHandler.formatResponse(new Error("wrong number of arguments for 'get' command"));
                    }
                    return RESPHandler.formatResponse(service.get(args.get(1)));
                case "SET":
                    if (args.size() != 3) {
                        return RESPHandler.formatResponse(new Error("wrong number of arguments for 'set' command"));
                    }
                    service.set(args.get(1), args.get(2));
                    return RESPHandler.formatResponse("OK");
                case "DEL":
                    if (args.size() != 2) {
                        return RESPHandler.formatResponse(new Error("wrong number of arguments for 'del' command"));
                    }
                    boolean deleted = service.del(args.get(1));
                    return RESPHandler.formatResponse(deleted ? 1L : 0L);
                case "INFO":
                    return RESPHandler.formatResponse(getInfo());
                case "PING":
                    return RESPHandler.formatResponse("PONG");
                case "KEYS":
                    if (args.size() != 2) {
                        return RESPHandler.formatResponse(new Error("wrong number of arguments for 'keys' command"));
                    }
                    Set<String> keys = service.keys(args.get(1));
                    return RESPHandler.formatResponse(keys.toArray(new String[0]));
                default:
                    return RESPHandler.formatResponse(new Error("unknown command '" + cmd + "'"));
            }
        }

        private String getInfo() {
            Map<String, String> info = new HashMap<>();
            info.put("redis_version", VERSION);
            info.put("uptime_in_seconds", String.valueOf((System.currentTimeMillis() - startTime) / 1000));
            info.put("connected_clients", String.valueOf(connectedClients));
            info.put("used_memory", String.valueOf(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
            info.put("used_memory_human", formatMemory(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
            info.put("total_system_memory", String.valueOf(Runtime.getRuntime().maxMemory()));
            info.put("total_system_memory_human", formatMemory(Runtime.getRuntime().maxMemory()));

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : info.entrySet()) {
                sb.append(entry.getKey()).append(":").append(entry.getValue()).append("\r\n");
            }
            return sb.toString();
        }

        private String formatMemory(long bytes) {
            final long K = 1024;
            final long M = K * K;
            final long G = M * K;
            if (bytes < K) return bytes + "B";
            if (bytes < M) return String.format("%.2fK", (float)bytes / K);
            if (bytes < G) return String.format("%.2fM", (float)bytes / M);
            return String.format("%.2fG", (float)bytes / G);
        }
    }
}