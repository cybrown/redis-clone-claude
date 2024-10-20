package org.cy.redisclone;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class CommandParser {
    private byte[] buffer = new byte[1024];
    private int position = 0;
    private Queue<byte[]> completeCommands = new LinkedList<>();

    /**
     * Appends incoming bytes to the internal buffer and parses commands.
     *
     * @param newBytes The incoming bytes to append
     */
    public void appendBytes(byte[] newBytes) {
        ensureCapacity(newBytes.length);
        System.arraycopy(newBytes, 0, buffer, position, newBytes.length);
        position += newBytes.length;
        parseCommands();
    }

    /**
     * Checks if another command is available.
     *
     * @return true if there's at least one complete command available, false otherwise
     */
    public boolean hasNextCommand() {
        return !completeCommands.isEmpty();
    }

    /**
     * Retrieves the next complete command.
     *
     * @return The next command as a byte array
     * @throws IllegalStateException if no command is available
     */
    public byte[] getNextCommand() {
        if (completeCommands.isEmpty()) {
            throw new IllegalStateException("No command available");
        }
        return completeCommands.poll();
    }

    private void ensureCapacity(int additionalCapacity) {
        if (position + additionalCapacity > buffer.length) {
            byte[] newBuffer = new byte[Math.max(buffer.length * 2, position + additionalCapacity)];
            System.arraycopy(buffer, 0, newBuffer, 0, position);
            buffer = newBuffer;
        }
    }

    private void parseCommands() {
        int index = 0;
        while (index < position) {
            if (buffer[index] == '*') {
                int arrayLength = parseInteger(index + 1);
                if (arrayLength == -1) return;
                index = skipLine(index);

                List<byte[]> elements = new ArrayList<>();
                for (int i = 0; i < arrayLength; i++) {
                    if (index >= position || buffer[index] != '$') return;
                    int strLength = parseInteger(index + 1);
                    if (strLength == -1) return;
                    index = skipLine(index);

                    if (index + strLength + 2 > position) return;
                    byte[] element = new byte[strLength];
                    System.arraycopy(buffer, index, element, 0, strLength);
                    elements.add(element);
                    index += strLength + 2; // Skip string content and \r\n
                }

                int commandLength = index;
                byte[] command = new byte[commandLength];
                System.arraycopy(buffer, 0, command, 0, commandLength);
                completeCommands.offer(command);

                System.arraycopy(buffer, commandLength, buffer, 0, position - commandLength);
                position -= commandLength;
                index = 0;
            } else {
                throw new IllegalStateException("Malformed RESP at position " + index);
            }
        }
    }

    private int parseInteger(int start) {
        int end = start;
        while (end < position && buffer[end] != '\r') {
            end++;
        }
        if (end + 1 >= position || buffer[end + 1] != '\n') {
            return -1;
        }
        String numStr = new String(buffer, start, end - start);
        return Integer.parseInt(numStr);
    }

    private int skipLine(int start) {
        while (start < position && buffer[start] != '\n') {
            start++;
        }
        return start + 1;
    }
}
