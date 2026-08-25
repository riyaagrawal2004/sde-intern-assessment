package com.indothai.orderupdate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvReaderTest {

    @TempDir
    Path tempDir;

    private Path writeCsv(String content) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readsAllValidRowsInOrder() throws IOException {
        Path csv = writeCsv(
                "event_id,symbol,transaction_type,quantity\n"
                        + "evt-0001,RELIANCE,BUY,90\n"
                        + "evt-0002,TCS,SELL,75\n"
        );

        List<CsvReader.RawRow> rows = readAll(csv);

        assertEquals(2, rows.size());
        assertEquals("evt-0001", rows.get(0).eventId);
        assertEquals("evt-0002", rows.get(1).eventId);
    }

    @Test
    void processingContinuesAfterAnInvalidRow() throws IOException {
        // Row 2 is structurally malformed (missing a column). The reader
        // must not stop or throw -- it should still hand back row 3 so the
        // validator can reject/accept rows independently of each other.
        Path csv = writeCsv(
                "event_id,symbol,transaction_type,quantity\n"
                        + "evt-0001,RELIANCE,BUY,90\n"
                        + "evt-0002,TCS,SELL\n"                 // malformed: missing quantity column
                        + "evt-0003,INFY,BUY,30\n"
        );

        RowValidator validator = new RowValidator();
        List<RowValidator.Result> results = new ArrayList<>();

        try (CsvReader reader = new CsvReader(csv.toString())) {
            CsvReader.RawRow row;
            while ((row = reader.readNext()) != null) {
                results.add(validator.validate(row.eventId, row.symbol, row.transactionType, row.quantityRaw));
            }
        }

        assertEquals(3, results.size());
        assertTrue(results.get(0).isValid());   // evt-0001 valid
        assertFalse(results.get(1).isValid());  // evt-0002 malformed row -> rejected
        assertTrue(results.get(2).isValid());   // evt-0003 still processed after the bad row
        assertEquals("evt-0003", results.get(2).event.eventId);
    }

    @Test
    void blankLinesAreSkippedWithoutBreakingSubsequentRows() throws IOException {
        Path csv = writeCsv(
                "event_id,symbol,transaction_type,quantity\n"
                        + "evt-0001,RELIANCE,BUY,90\n"
                        + "\n"
                        + "evt-0002,TCS,SELL,75\n"
        );

        List<CsvReader.RawRow> rows = readAll(csv);

        assertEquals(2, rows.size());
        assertEquals("evt-0002", rows.get(1).eventId);
    }

    @Test
    void doesNotThrowOnEmptyFileWithOnlyHeader() throws IOException {
        Path csv = writeCsv("event_id,symbol,transaction_type,quantity\n");

        List<CsvReader.RawRow> rows = readAll(csv);

        assertTrue(rows.isEmpty());
    }

    private List<CsvReader.RawRow> readAll(Path csv) throws IOException {
        List<CsvReader.RawRow> rows = new ArrayList<>();
        try (CsvReader reader = new CsvReader(csv.toString())) {
            CsvReader.RawRow row;
            while ((row = reader.readNext()) != null) {
                rows.add(row);
            }
        }
        return rows;
    }
}