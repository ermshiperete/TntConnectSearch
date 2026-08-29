package com.ermshiperete.tntconnectsearch;

import com.ermshiperete.tntconnectsearch.model.SearchResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "tntconnect-search",
        mixinStandardHelpOptions = true,
        version = "tntconnect-search 1.0.0",
        description = "Search TntConnect History entries for a term and output matching contact names and cities."
)
public class TntConnectSearchApp implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the TntConnect .mpddb database file")
    private String dbPath;

    @Parameters(index = "1", description = "Search term (case-insensitive)")
    private String searchTerm;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD_RED = "\u001B[1;31m";

    @Override
    public Integer call() {
        try (SearchService service = new SearchService(dbPath)) {
            List<SearchResult> results = service.search(searchTerm);

            if (results.isEmpty()) {
                System.out.println("No matches found.");
                return 0;
            }

            System.out.println("Found " + results.size() + " matching contact(s) for \"" + searchTerm + "\":");
            System.out.println();
            printTable(results);
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void printTable(List<SearchResult> results) {
        int maxNameWidth = "Name".length();
        int maxCityWidth = "City".length();

        for (SearchResult r : results) {
            maxNameWidth = Math.max(maxNameWidth, r.name().length());
            maxCityWidth = Math.max(maxCityWidth, r.city().length());
        }

        System.out.printf("%-" + maxNameWidth + "s  %-" + maxCityWidth + "s  %s%n", "Name", "City", "Matched Content");
        for (SearchResult r : results) {
            String highlighted = highlightTerm(r.foundIn(), searchTerm);
            System.out.printf("%-" + maxNameWidth + "s  %-" + maxCityWidth + "s  %s%n", r.name(), r.city(), highlighted);
        }
    }

    private String highlightTerm(String text, String term) {
        if (term.isEmpty()) {
            return text;
        }
        String lowerText = text.toLowerCase();
        String lowerTerm = term.toLowerCase();
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        int idx;
        while ((idx = lowerText.indexOf(lowerTerm, lastEnd)) >= 0) {
            sb.append(text, lastEnd, idx);
            sb.append(ANSI_BOLD_RED);
            sb.append(text, idx, idx + term.length());
            sb.append(ANSI_RESET);
            lastEnd = idx + term.length();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TntConnectSearchApp()).execute(args);
        System.exit(exitCode);
    }
}
