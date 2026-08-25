package com.indothai.orderupdate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads order_updates.csv one line at a time (streaming), instead of
 * loading the whole file into memory -- matches the PDF requirement:
 * "Read the CSV incrementally, one row at a time. Do not load the
 * entire file into memory."
 *
 * Expected header: event_id,symbol,transaction_type,quantity
 */
public class CsvReader implements AutoCloseable {

    private final BufferedReader reader;
    private String[] header;
    private int lineNumber = 0;

    public CsvReader(String filePath) throws IOException {
        this.reader = new BufferedReader(new FileReader(filePath));
        String headerLine = reader.readLine();
        lineNumber++;
        if (headerLine == null) {
            throw new IOException("CSV file is empty, missing header row: " + filePath);
        }
        this.header = splitCsvLine(headerLine);
    }

    /** Represents one raw row, before validation. */
    public static class RawRow {
        public final int lineNumber;
        public final String eventId;
        public final String symbol;
        public final String transactionType;
        public final String quantityRaw;

        RawRow(int lineNumber, String eventId, String symbol, String transactionType, String quantityRaw) {
            this.lineNumber = lineNumber;
            this.eventId = eventId;
            this.symbol = symbol;
            this.transactionType = transactionType;
            this.quantityRaw = quantityRaw;
        }
    }

    /**
     * Reads and returns the next raw row, or null at end of file.
     * A structurally malformed row (wrong column count) is returned with
     * whatever fields could be read as blank/null so the validator can
     * reject it cleanly, rather than throwing and stopping processing.
     */
    public RawRow readNext() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        lineNumber++;

        // Skip fully blank lines (e.g. trailing newline at EOF) without
        // treating them as a malformed data row.
        if (line.isBlank()) {
            return readNext();
        }

        String[] fields = splitCsvLine(line);
        String eventId = fieldAt(fields, indexOf("event_id"));
        String symbol = fieldAt(fields, indexOf("symbol"));
        String transactionType = fieldAt(fields, indexOf("transaction_type"));
        String quantityRaw = fieldAt(fields, indexOf("quantity"));

        return new RawRow(lineNumber, eventId, symbol, transactionType, quantityRaw);
    }

    public int currentLineNumber() {
        return lineNumber;
    }

    private int indexOf(String columnName) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equals(columnName)) return i;
        }
        return -1;
    }

    private static String fieldAt(String[] fields, int index) {
        if (index < 0 || index >= fields.length) return null;
        return fields[index];
    }

    /** Very small CSV splitter -- sufficient for this dataset (no quoted commas). */
    private static String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}