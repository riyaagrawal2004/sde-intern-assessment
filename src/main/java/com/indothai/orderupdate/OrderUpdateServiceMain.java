package com.indothai.orderupdate;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Entry point for the Order Update Service.
 *
 * Reads order_updates.csv incrementally, validates each row against the
 * Event Contract, throttles emission to a configurable max rate, and sends
 * valid events to the Position Maintaining Service over HTTP -- in CSV
 * order. Invalid rows are logged with a reason and skipped without
 * stopping the rest of the file from being processed.
 *
 * Runs independently from the Position Maintaining Service (separate JVM
 * process). All of input file path, service address, and port are
 * configurable via command-line args:
 *
 *   java -cp target/classes com.indothai.orderupdate.OrderUpdateServiceMain \
 *        [csvFilePath] [positionServiceBaseUrl] [maxEventsPerSecond]
 *
 * Defaults: csvFilePath=order_updates.csv,
 *           positionServiceBaseUrl=http://localhost:8080,
 *           maxEventsPerSecond=50
 */
public class OrderUpdateServiceMain {

    private static final Logger LOGGER = Logger.getLogger(OrderUpdateServiceMain.class.getName());

    public static void main(String[] args) {
        String csvFilePath = args.length > 0 ? args[0] : "order_updates.csv";
        String positionServiceBaseUrl = args.length > 1 ? args[1] : "http://localhost:8080";
        int maxEventsPerSecond = args.length > 2 ? Integer.parseInt(args[2]) : 50;

        LOGGER.info("Starting Order Update Service. csvFilePath=" + csvFilePath
                + " positionServiceBaseUrl=" + positionServiceBaseUrl
                + " maxEventsPerSecond=" + maxEventsPerSecond);

        RowValidator validator = new RowValidator();
        Throttler throttler = new Throttler(maxEventsPerSecond);
        PositionServiceClient client = new PositionServiceClient(positionServiceBaseUrl);

        int acceptedCount = 0;
        int rejectedCount = 0;
        int sentCount = 0;
        int sendFailedCount = 0;

        try (CsvReader csvReader = new CsvReader(csvFilePath)) {
            CsvReader.RawRow row;
            while ((row = csvReader.readNext()) != null) {
                RowValidator.Result result = validator.validate(
                        row.eventId, row.symbol, row.transactionType, row.quantityRaw);

                if (!result.isValid()) {
                    rejectedCount++;
                    LOGGER.warning("Rejected row at line " + row.lineNumber + ": " + result.rejectionReason);
                    continue;
                }

                acceptedCount++;
                OrderEvent event = result.event;
                final int lineNumberForLog = row.lineNumber;
                LOGGER.fine(() -> "Accepted event " + event.eventId + " at line " + lineNumberForLog);

                throttler.throttle();

                boolean sent = client.send(event);
                if (sent) {
                    sentCount++;
                } else {
                    sendFailedCount++;
                    LOGGER.warning("Failed to send event " + event.eventId + " to Position Service");
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Fatal error reading CSV file '" + csvFilePath + "': " + e.getMessage());
            System.exit(1);
        }

        LOGGER.info("Input processing complete. accepted=" + acceptedCount
                + " rejected=" + rejectedCount
                + " sent=" + sentCount
                + " sendFailed=" + sendFailedCount);
    }
}