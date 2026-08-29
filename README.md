# TntConnect History Search CLI

A command-line tool to search TntConnect History entries for a term and output matching contact names, cities, and the matched content.

## What It Does

- Searches all text fields in TntConnect `History` entries and related `Contact` records
- Outputs matching contact names, cities, and a snippet of the field content where the match was found
- Results are sorted alphabetically by contact name
- Each contact appears only once, even if the search term matches multiple history entries
- The search term is highlighted in bold red in the output

## Prerequisites

- Java 17 or later
- Maven 3.6+ (for building)
- The TntConnect database file (.mpddb) must not be open in TntConnect (the tool opens it in exclusive mode)

## Building

```bash
mvn clean package
```

This creates a fat JAR at `target/tntconnect-search.jar` with all dependencies included.

## Usage

```bash
java -jar target/tntconnect-search.jar <database-path> <search-term>
```

### Arguments

- `database-path` — Path to the TntConnect .mpddb database file
- `search-term` — Term to search for (case-insensitive)

### Examples

```bash
java -jar target/tntconnect-search.jar /path/to/tntdata.mpddb "smith"
java -jar target/tntconnect-search.jar /path/to/tntdata.mpddb "meeting"
```

### Output

```
Found 3 matching contact(s) for "smith":

Name              City           Matched Content
John Smith        Springfield    Description: ...meeting with John Smith about...
Jane Doe          Chicago        LastName: Smith
Bob Wilson        New York       Notes (History): ...contacted Smith regarding...
```

- The first column shows the contact's full name
- The second column shows the mailing city
- The third column shows the field name and a truncated snippet of the matching content, with the search term highlighted in bold red

### Exit Codes

- `0` — Success (or no matches found)
- `1` — Runtime error (database connection failure, query error)
- `2` — Invalid arguments (database file not found or not readable)

## Search Fields

The following fields are searched:

**History table:**
- Description
- Notes
- AutoGenCode
- DataChangeLogAsCsv

**Contact table:**
- FullName
- FirstName
- LastName
- OrganizationName
- Notes
- NotesAsRtf

## Technical Details

- Uses [UCanAccess](https://ucanaccess.sourceforge.net/) JDBC driver to read Microsoft Access .mpddb files
- Connection is read-only with `openExclusive=true`
- Built with [picocli](https://picocli.info/) for CLI argument parsing
