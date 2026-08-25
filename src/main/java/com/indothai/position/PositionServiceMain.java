package com.indothai.position;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Entry point for the Position Maintaining Service.
 *
 * Runs independently from the Order Update Service (separate JVM process).
 * Host and port are configurable via command-line args so this isn't tied
 * to one machine-specific setup:
 *
 *   java -cp target/classes com.indothai.position.PositionServiceMain [host] [port]
 *
 * Defaults: host=0.0.0.0, port=8080
 */
public class PositionServiceMain {

    private static final Logger LOGGER = Logger.getLogger(PositionServiceMain.class.getName());

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "0.0.0.0";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        PositionStore store = new PositionStore();

        try {
            PositionHttpServer server = new PositionHttpServer(store, host, port);
            server.start();
            LOGGER.info("Position Maintaining Service started. Waiting for events on POST /events, "
                    + "serving positions on GET /position. Press Ctrl+C to stop.");
        } catch (IOException e) {
            LOGGER.severe("Failed to start Position Maintaining Service: " + e.getMessage());
            System.exit(1);
        }
    }
}