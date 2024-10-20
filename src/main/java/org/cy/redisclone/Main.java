package org.cy.redisclone;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException {
		Logger rootLogger = Logger.getLogger("");
		rootLogger.setLevel(Level.INFO);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.INFO);
		rootLogger.addHandler(handler);

		RedisLikeService service = new RedisLikeService();
		RedisServer server = new RedisServer(6379, service);
		server.start();
	}
}
