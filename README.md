# Redis Clone

## Project Goal

This project implements a simplified clone of Redis, a popular in-memory data structure store. The goal is to create a basic Redis-like server that supports a subset of Redis commands using the Redis Serialization Protocol (RESP).

Key features include:
- Basic Redis commands: GET, SET, DEL, KEYS
- INFO command for server statistics
- PING command for connection testing
- RESP (Redis Serialization Protocol) support

This project serves as an educational tool to understand the fundamentals of Redis and network programming in Java.

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21
- Maven 3.6 or higher

### Building the Project

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/redis-clone.git
   cd redis-clone
   ```

2. Build the project using Maven:
   ```
   mvn clean package
   ```

### Running the Server

To start the Redis clone server, you can use the Spring Boot Maven plugin:

```
mvn spring-boot:run
```

Alternatively, you can run the generated JAR file:

```
java -jar target/redisclone-0.0.1-SNAPSHOT.jar
```

The server will start on port 6379 by default.

### Running Tests

To run the integration tests, use the following Maven command:

```
mvn test
```

These tests will start the server, run various commands using the Lettuce Redis client, and verify the results.

## Connecting to the Server

You can connect to the server using any Redis client. For example, using the `redis-cli`:

```
redis-cli -p 6379
```

Or you can use a Redis client library in your preferred programming language.

## Supported Commands

- `GET key`: Get the value of a key
- `SET key value`: Set the value of a key
- `DEL key`: Delete a key
- `KEYS pattern`: Find all keys matching the given pattern
- `INFO`: Get information and statistics about the server
- `PING`: Test if the server is responsive

## Project Structure

- `org.cy.redisclone.Main`: Entry point of the application, starts the server
- `org.cy.redisclone.RedisServer`: Main server class
- `org.cy.redisclone.RedisLikeService`: Core data storage and retrieval logic
- `org.cy.redisclone.RESPHandler`: RESP protocol parser and formatter
- `org.cy.redisclone.RedisCloneIntegrationTest`: Integration tests for the Redis clone server

## Dependencies

- Java 21
- Lombok 1.18.34 (for reducing boilerplate code)
- Spring Boot Maven Plugin 3.3.4 (for building and running the application)
- JUnit Jupiter 5.8.2 (for testing)
- Lettuce 6.2.3.RELEASE (Redis client for testing)

## Building and Packaging

This project uses Maven for dependency management and building. The `pom.xml` file includes configurations for:

- Java version 21
- Lombok for code generation
- Spring Boot Maven Plugin for creating an executable JAR
- JUnit and Lettuce for integration testing

To build the project, simply run `mvn clean package`. This will compile the code, run the tests, and create an executable JAR file in the `target` directory.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source and available under the [MIT License](LICENSE).