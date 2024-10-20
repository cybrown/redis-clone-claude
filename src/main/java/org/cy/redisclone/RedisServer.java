package org.cy.redisclone;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class RedisServer {
    private static final Logger LOGGER = Logger.getLogger(RedisServer.class.getName());
    private static final String VERSION = "1.0.0";
    private static long startTime;
    private static int connectedClients = 0;
    private final int port;
    private final RedisLikeService service;
    private final ConcurrentHashMap<SocketAddress, ClientInfo> clients = new ConcurrentHashMap<>();

    public RedisServer(int port, RedisLikeService service) {
        this.port = port;
        this.service = service;
        startTime = System.currentTimeMillis();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info("Server listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, service, clients)).start();
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final RedisLikeService service;
        private final ConcurrentHashMap<SocketAddress, ClientInfo> clients;
        private String clientName = "";

        public ClientHandler(Socket socket, RedisLikeService service, ConcurrentHashMap<SocketAddress, ClientInfo> clients) {
            this.clientSocket = socket;
            this.service = service;
            this.clients = clients;
            connectedClients++;
            clients.put(socket.getRemoteSocketAddress(), new ClientInfo(socket.getRemoteSocketAddress(), ""));
        }

        @Override
        public void run() {
            LOGGER.info("New client connected: " + clientSocket.getInetAddress());
            try (
                    InputStream in = clientSocket.getInputStream();
                    OutputStream out = clientSocket.getOutputStream()
            ) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                CommandParser parser = new CommandParser();

                while ((bytesRead = in.read(buffer)) != -1) {
                    parser.appendBytes(Arrays.copyOf(buffer, bytesRead));

                    while (parser.hasNextCommand()) {
                        try {
                            byte[] commandBytes = parser.getNextCommand();
                            String command = new String(commandBytes, StandardCharsets.UTF_8);
                            List<Object> parsed = RESPHandler.parseRequest(command);
                            String response = executeCommand(parsed);
                            out.write(response.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IllegalStateException e) {
                            // This shouldn't happen because we check hasNextCommand(), but handle it just in case
                            LOGGER.warning("Unexpected error: " + e.getMessage());
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error handling client", e);
            } finally {
                try {
                    clientSocket.close();
                    LOGGER.info("Client disconnected: " + clientSocket.getInetAddress());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error closing client socket", e);
                }
                connectedClients--;
                clients.remove(clientSocket.getRemoteSocketAddress());
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
                case "KEYS":
                    if (args.size() != 2) {
                        return RESPHandler.formatResponse(new Error("wrong number of arguments for 'keys' command"));
                    }
                    Set<String> keys = service.keys(args.get(1));
                    return RESPHandler.formatResponse(keys.toArray(new String[0]));
                case "INFO":
                    return RESPHandler.formatResponse(getInfo());
                case "PING":
                    return RESPHandler.formatResponse("PONG");
                case "QUIT":
                    return RESPHandler.formatResponse("OK");
                case "CLIENT":
                    if (args.size() < 2) {
                        return RESPHandler.formatResponse(new Error("Wrong number of arguments for CLIENT command"));
                    }
                    String subCommand = args.get(1).toUpperCase();
                    switch (subCommand) {
                        case "LIST":
                            return RESPHandler.formatResponse(clientList());
                        case "SETNAME":
                            if (args.size() != 3) {
                                return RESPHandler.formatResponse(new Error("Wrong number of arguments for CLIENT SETNAME"));
                            }
                            return RESPHandler.formatResponse(clientSetName(args.get(2)));
                        case "SETINFO":
                            if (args.size() != 4) {
                                return RESPHandler.formatResponse(new Error("Wrong number of arguments for CLIENT SETINFO"));
                            }
                            return RESPHandler.formatResponse(clientSetInfo(args.get(2), args.get(3)));
                        default:
                            return RESPHandler.formatResponse(new Error("Unknown CLIENT subcommand"));
                    }
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

        private String clientList() {
            return clients.values().stream()
                    .map(ClientInfo::toString)
                    .collect(Collectors.joining("\n"));
        }

        private String clientSetName(String name) {
            clientName = name;
            clients.get(clientSocket.getRemoteSocketAddress()).setName(name);
            return "OK";
        }

        private String clientSetInfo(String field, String value) {
            ClientInfo clientInfo = clients.get(clientSocket.getRemoteSocketAddress());
            if (clientInfo != null) {
                clientInfo.setInfo(field, value);
                return "OK";
            }
            return "ERR Client not found";
        }
    }

    private static class ClientInfo {
        private final SocketAddress address;
        private String name;
        private final Map<String, String> info = new HashMap<>();

        public ClientInfo(SocketAddress address, String name) {
            this.address = address;
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setInfo(String field, String value) {
            info.put(field, value);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("id=").append(address.toString())
                    .append(" name=").append(name)
                    .append(" addr=").append(address.toString())
                    .append(" fd=0 age=0 idle=0 flags=N db=0 sub=0 psub=0 multi=-1 qbuf=0 qbuf-free=0 obl=0 oll=0 omem=0 events=r cmd=NULL");

            for (Map.Entry<String, String> entry : info.entrySet()) {
                sb.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
            }

            return sb.toString();
        }
    }
}