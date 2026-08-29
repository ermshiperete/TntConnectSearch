package com.ermshiperete.tntconnectsearch;

import com.ermshiperete.tntconnectsearch.model.SearchResult;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchService implements AutoCloseable {

    private static final String DRIVER_CLASS = "net.ucanaccess.jdbc.UcanaccessDriver";
    private static final String DB_PASSWORD = "tntMPD";

    private static final String SEARCH_SQL = """
            SELECT c.ContactID, c.FullName, c.MailingCity,
                h.Description AS H_Description, h.Notes AS H_Notes,
                h.AutoGenCode AS H_AutoGenCode, h.DataChangeLogAsCsv AS H_DataChangeLogAsCsv,
                c.FirstName, c.LastName, c.OrganizationName,
                c.Notes AS C_Notes, c.NotesAsRtf AS C_NotesAsRtf
            FROM History h
            JOIN HistoryContact hc ON h.HistoryID = hc.HistoryID
            JOIN Contact c ON hc.ContactID = c.ContactID
            WHERE LOWER(
                COALESCE(h.Description, '') || ' ' ||
                COALESCE(h.Notes, '') || ' ' ||
                COALESCE(h.AutoGenCode, '') || ' ' ||
                COALESCE(h.DataChangeLogAsCsv, '') || ' ' ||
                COALESCE(c.FullName, '') || ' ' ||
                COALESCE(c.FirstName, '') || ' ' ||
                COALESCE(c.LastName, '') || ' ' ||
                COALESCE(c.OrganizationName, '') || ' ' ||
                COALESCE(c.Notes, '') || ' ' ||
                COALESCE(c.NotesAsRtf, '')
            ) LIKE ?
            """;

    private final Connection connection;

    public SearchService(String dbPath) {
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            throw new IllegalArgumentException("Database file not found: " + dbPath);
        }
        if (!dbFile.canRead()) {
            throw new IllegalArgumentException("Database file is not readable: " + dbPath);
        }

        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("UCanAccess driver not found on classpath", e);
        }

        String url = "jdbc:ucanaccess://" + dbFile.getAbsolutePath()
                + ";immediatelyReleaseResources=true;openExclusive=true;";

        try {
            this.connection = DriverManager.getConnection(url, "", DB_PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private static final int SNIPPET_PADDING = 30;
    private static final int MAX_SNIPPET_LENGTH = 80;

    public List<SearchResult> search(String term) {
        Map<Integer, SearchResultBuilder> contactMap = new LinkedHashMap<>();
        String termLower = term.toLowerCase();
        String pattern = "%" + termLower + "%";

        try (PreparedStatement stmt = connection.prepareStatement(SEARCH_SQL)) {
            stmt.setString(1, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int contactId = rs.getInt("ContactID");
                    String name = rs.getString("FullName");
                    String city = rs.getString("MailingCity");

                    List<String> matchedParts = new ArrayList<>();
                    extractMatch(rs, "H_Description", "Description", term, termLower, matchedParts);
                    extractMatch(rs, "H_Notes", "Notes (History)", term, termLower, matchedParts);
                    extractMatch(rs, "H_AutoGenCode", "AutoGenCode", term, termLower, matchedParts);
                    extractMatch(rs, "H_DataChangeLogAsCsv", "DataChangeLogAsCsv", term, termLower, matchedParts);
                    extractMatch(rs, "FirstName", "FirstName", term, termLower, matchedParts);
                    extractMatch(rs, "LastName", "LastName", term, termLower, matchedParts);
                    extractMatch(rs, "OrganizationName", "OrganizationName", term, termLower, matchedParts);

                    String contactNotes = rs.getString("C_Notes");
                    if (contactNotes != null && contactNotes.toLowerCase().contains(termLower)) {
                        matchedParts.add("Notes (Contact): " + truncateMatch(contactNotes, term));
                    }
                    String notesAsRtf = rs.getString("C_NotesAsRtf");
                    if (notesAsRtf != null && notesAsRtf.toLowerCase().contains(termLower)) {
                        matchedParts.add("NotesAsRtf (Contact): " + truncateMatch(notesAsRtf, term));
                    }

                    contactMap.computeIfAbsent(contactId, k -> new SearchResultBuilder(
                            name != null ? name : "",
                            city != null ? city : ""
                    )).addFirstMatch(matchedParts);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Search query failed: " + e.getMessage(), e);
        }

        List<SearchResult> results = new ArrayList<>();
        for (SearchResultBuilder builder : contactMap.values()) {
            results.add(builder.build());
        }
        results.sort(Comparator.comparing(SearchResult::name, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    private static class SearchResultBuilder {
        private final String name;
        private final String city;
        private String firstMatch;

        SearchResultBuilder(String name, String city) {
            this.name = name;
            this.city = city;
        }

        void addFirstMatch(List<String> parts) {
            if (firstMatch == null && !parts.isEmpty()) {
                firstMatch = parts.get(0);
            }
        }

        SearchResult build() {
            return new SearchResult(name, city, firstMatch != null ? firstMatch : "");
        }
    }

    private void extractMatch(ResultSet rs, String columnName, String displayName, String term, String termLower, List<String> matchedParts) throws SQLException {
        String value = rs.getString(columnName);
        if (value != null && value.toLowerCase().contains(termLower)) {
            matchedParts.add(displayName + ": " + truncateMatch(value, term));
        }
    }

    private String truncateMatch(String text, String term) {
        text = text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        String textLower = text.toLowerCase();
        String termLower = term.toLowerCase();
        int matchIndex = textLower.indexOf(termLower);

        if (matchIndex < 0) {
            return text.length() > MAX_SNIPPET_LENGTH ? text.substring(0, MAX_SNIPPET_LENGTH - 3) + "..." : text;
        }

        int start = Math.max(0, matchIndex - SNIPPET_PADDING);
        int end = Math.min(text.length(), matchIndex + term.length() + SNIPPET_PADDING);

        StringBuilder sb = new StringBuilder();
        if (start > 0) {
            sb.append("...");
        }
        sb.append(text, start, end);
        if (end < text.length()) {
            sb.append("...");
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Warning: failed to close database connection: " + e.getMessage());
            }
        }
    }
}
