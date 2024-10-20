package org.cy.redisclone;

import java.util.ArrayList;
import java.util.List;

public class RESPHandler {

    public static String formatResponse(Object response) {
        StringBuilder sb = new StringBuilder();
        if (response == null) {
            sb.append("$-1\r\n");
        } else if (response instanceof String) {
            String strResponse = (String) response;
            if (strResponse.equals("OK") || strResponse.equals("PONG")) {
                sb.append("+").append(strResponse).append("\r\n");
            } else {
                sb.append("$").append(strResponse.length()).append("\r\n");
                sb.append(strResponse).append("\r\n");
            }
        } else if (response instanceof Long) {
            sb.append(":").append(response).append("\r\n");
        } else if (response instanceof List) {
            List<?> list = (List<?>) response;
            sb.append("*").append(list.size()).append("\r\n");
            for (Object item : list) {
                sb.append(formatResponse(item));
            }
        } else if (response instanceof String[]) {
            String[] array = (String[]) response;
            sb.append("*").append(array.length).append("\r\n");
            for (String item : array) {
                sb.append("$").append(item.length()).append("\r\n");
                sb.append(item).append("\r\n");
            }
        } else if (response instanceof Error) {
            sb.append("-ERR ").append(((Error) response).getMessage()).append("\r\n");
        } else {
            throw new IllegalArgumentException("Unsupported response type: " + response.getClass());
        }
        return sb.toString();
    }

    public static List<Object> parseRequest(String input) throws IllegalArgumentException {
        List<Object> parsed = new ArrayList<>();
        String[] lines = input.split("\r\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }
            char type = lines[i].charAt(0);
            String value = lines[i].substring(1);
            switch (type) {
                case '*':
                    int arrayLength = Integer.parseInt(value);
                    List<Object> array = new ArrayList<>();
                    for (int j = 0; j < arrayLength; j++) {
                        if (++i >= lines.length) {
                            throw new IllegalArgumentException("Incomplete RESP array");
                        }
                        array.add(parseBulkString(lines, i));
                        if (lines[i].startsWith("$") && !lines[i].equals("$-1")) {
                            i++;
                        }
                    }
                    parsed.add(array);
                    break;
                case '+':
                case ':':
                case '$':
                    parsed.add(parseBulkString(lines, i));
                    if (type == '$' && !value.equals("-1")) {
                        i++;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported RESP type: " + type);
            }
        }
        return parsed;
    }

    private static String parseBulkString(String[] lines, int i) throws IllegalArgumentException {
        if (lines[i].startsWith("$")) {
            int length = Integer.parseInt(lines[i].substring(1));
            if (length == -1) {
                return null;
            }
            if (i + 1 >= lines.length) {
                throw new IllegalArgumentException("Incomplete bulk string");
            }
            return lines[i + 1];
        } else {
            return lines[i].substring(1);
        }
    }
}