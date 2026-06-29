package com.example.educationstatisticscompiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class CsvDataService {

    private final List<CsvRecord> records;
    private final Map<String, String> dictionaryDefinitions;

    public CsvDataService() {
        this.dictionaryDefinitions = loadDictionaryDefinitions();
        this.records = loadRecords();
    }

    public List<CsvRecord> search(String query) {
        String normalized = normalize(query);
        if (normalized == null || normalized.isBlank()) {
            return records.stream().limit(20).toList();
        }

        String[] terms = Arrays.stream(normalized.split("\\s+"))
                .filter(term -> !term.isBlank() && !isStopWord(term))
                .toArray(String[]::new);
        return records.stream()
                .filter(record -> matchesQuery(record, terms))
                .sorted((left, right) -> Integer.compare(scoreRecord(right, terms), scoreRecord(left, terms)))
                .limit(50)
                .toList();
    }

    public List<CsvRecord> searchByPlace(String place, String county, String topic, String sortOrder, int limit) {
        String normalizedPlace = normalize(place);
        String normalizedCounty = normalize(county);
        String normalizedTopic = normalize(topic);

        return records.stream()
                .filter(this::hasMeaningfulEstimate)
                .filter(record -> matchesPlaceFilter(record, normalizedPlace, normalizedCounty))
                .filter(record -> matchesTopicFilter(record, normalizedTopic))
                .sorted(Comparator.comparingInt((CsvRecord record) -> estimateValue(record.estimate())).reversed())
                .limit(limit)
                .toList();
    }

    public List<CsvRecord> searchByFilters(String scope, String location, String race, String sortOrder, int limit) {
        String normalizedScope = normalize(scope);
        String normalizedLocation = normalize(location);
        String normalizedRace = normalize(race);
        String normalizedSort = normalize(sortOrder);

        return records.stream()
                .filter(this::hasMeaningfulEstimate)
                .filter(record -> matchesScopeFilter(record, normalizedScope, normalizedLocation))
                .filter(record -> matchesRaceFilter(record, normalizedRace))
                .sorted((left, right) -> {
                    int leftValue = estimateValue(left.estimate());
                    int rightValue = estimateValue(right.estimate());
                    return normalizedSort != null && normalizedSort.contains("low")
                            ? Integer.compare(leftValue, rightValue)
                            : Integer.compare(rightValue, leftValue);
                })
                .limit(limit)
                .toList();
    }

    public String getMeasureAvailability(String measure) {
        String normalizedMeasure = normalize(measure);
        if (normalizedMeasure == null || normalizedMeasure.isBlank()) {
            return "No measure requested.";
        }

        boolean available = records.stream()
                .anyMatch(record -> record.indicator().toLowerCase(Locale.ROOT).contains(normalizedMeasure));

        return available
                ? "The measure '" + measure + "' is available in the current dataset."
                : "The measure '" + measure + "' is not currently available in the included dataset.";
    }

    public List<String> getGuidanceSuggestions() {
        return List.of(
                "Try: highest college-degree rates for Los Angeles",
                "Try: lowest rates in the Bay Area",
                "Try: Asian residents across California",
                "Try: Latino rates by region"
        );
    }

    public String getIndicatorSummary() {
        String definition = dictionaryDefinitions.getOrDefault("ind_definition", "");
        if (definition.isBlank()) {
            return "This dataset shows the share of adults age 25 and up with a four-year college degree or higher.";
        }
        return definition + " This is a percentage-based measure, so higher values mean a larger share of adults in that place have a college degree.";
    }

    private boolean matchesQuery(CsvRecord record, String[] terms) {
        if (terms.length == 0) {
            return true;
        }

        String searchable = String.join(" ",
                record.indicator(),
                record.raceEthName(),
                record.geotype(),
                record.geoname(),
                record.countyName(),
                record.regionName(),
                record.estimate(),
                record.geotypeValue()
        ).toLowerCase(Locale.ROOT);

        boolean raceMatch = false;
        boolean locationMatch = false;

        for (String term : terms) {
            if (matchesGroupAlias(term, record)) {
                raceMatch = true;
            }
            if (looksLikeLocationTerm(term) && searchable.contains(term)) {
                locationMatch = true;
            }
        }

        return raceMatch || locationMatch || Arrays.stream(terms).anyMatch(searchable::contains);
    }

    private int scoreRecord(CsvRecord record, String[] terms) {
        String searchable = String.join(" ",
                record.indicator(),
                record.raceEthName(),
                record.geotype(),
                record.geoname(),
                record.countyName(),
                record.regionName(),
                record.estimate(),
                record.geotypeValue()
        ).toLowerCase(Locale.ROOT);

        int score = 0;
        for (String term : terms) {
            if (matchesGroupAlias(term, record)) {
                score += 8;
            }
            if (searchable.contains(term)) {
                score += 3;
            }
            if (looksLikeLocationTerm(term) && containsLocationField(term, record)) {
                score += 5;
            }
        }
        if (searchable.contains("los angeles")) {
            score += 10;
        }
        return score;
    }

    private boolean hasMeaningfulEstimate(CsvRecord record) {
        String estimate = record.estimate();
        if (estimate == null || estimate.isBlank()) {
            return false;
        }
        String normalizedEstimate = estimate.trim();
        return !normalizedEstimate.equalsIgnoreCase("NA") && !normalizedEstimate.equalsIgnoreCase("N/A") && !normalizedEstimate.equalsIgnoreCase("null");
    }

    private boolean matchesPlaceFilter(CsvRecord record, String place, String county) {
        if ((place == null || place.isBlank()) && (county == null || county.isBlank())) {
            return true;
        }

        String searchableLocation = String.join(" ",
                record.geoname(),
                record.countyName(),
                record.regionName(),
                record.geotypeValue()
        ).toLowerCase(Locale.ROOT);

        boolean placeMatch = place != null && !place.isBlank() && searchableLocation.contains(place);
        boolean countyMatch = county != null && !county.isBlank() && searchableLocation.contains(county);
        return placeMatch || countyMatch;
    }

    private boolean matchesScopeFilter(CsvRecord record, String scope, String location) {
        if (location == null || location.isBlank()) {
            return scope == null || scope.isBlank() || scope.contains("all") || scope.contains("state");
        }

        String searchableLocation = String.join(" ", record.geoname(), record.countyName(), record.regionName(), record.geotypeValue()).toLowerCase(Locale.ROOT);
        if (scope != null && scope.contains("region")) {
            return searchableLocation.contains(location) || record.regionName().toLowerCase(Locale.ROOT).contains(location);
        }
        return searchableLocation.contains(location);
    }

    private boolean matchesRaceFilter(CsvRecord record, String race) {
        if (race == null || race.isBlank() || race.contains("all")) {
            return true;
        }
        String searchableRace = record.raceEthName().toLowerCase(Locale.ROOT);
        return searchableRace.contains(race);
    }

    private boolean matchesTopicFilter(CsvRecord record, String topic) {
        if (topic == null || topic.isBlank()) {
            return true;
        }
        String searchableTopic = String.join(" ", record.indicator(), record.raceEthName(), record.geotype()).toLowerCase(Locale.ROOT);
        return searchableTopic.contains(topic);
    }

    private boolean containsLocationField(String term, CsvRecord record) {
        String location = String.join(" ", record.geoname(), record.countyName(), record.regionName(), record.geotypeValue()).toLowerCase(Locale.ROOT);
        return location.contains(term);
    }

    private boolean looksLikeLocationTerm(String term) {
        if (term.length() <= 2 || isStopWord(term)) {
            return false;
        }
        return !isRaceTerm(term);
    }

    private boolean isRaceTerm(String term) {
        return Set.of("african", "american", "latino", "hispanic", "asian", "white", "native", "indian", "alaskan", "aian", "pacific", "islander", "nhopi", "black", "africanam").contains(term);
    }

    private boolean matchesGroupAlias(String term, CsvRecord record) {
        String race = record.raceEthName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (term) {
            case "african", "africanamerican", "black", "africanam" -> race.contains("african") || race.contains("black");
            case "latino", "hispanic", "latina" -> race.contains("latino") || race.contains("hispanic");
            case "asian" -> race.contains("asian");
            case "white" -> race.contains("white");
            case "native", "indian", "alaskan", "aian", "americanindian" -> race.contains("indian") || race.contains("aian");
            case "pacific", "islander", "nhopi" -> race.contains("pacific") || race.contains("nhopi");
            case "american" -> race.contains("american") || race.contains("african");
            default -> false;
        };
    }

    private boolean isStopWord(String term) {
        return Set.of("in", "the", "and", "for", "with", "of", "a", "an").contains(term);
    }

    private Map<String, String> loadDictionaryDefinitions() {
        Map<String, String> definitions = new HashMap<>();
        try {
            ClassPathResource resource = new ClassPathResource("datadictionary.csv");
            try (InputStream inputStream = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean headerSkipped = false;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    String[] parts = splitCsvLine(line);
                    if (parts.length >= 2) {
                        definitions.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load data dictionary", e);
        }
        return definitions;
    }

    private List<CsvRecord> loadRecords() {
        List<CsvRecord> loaded = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource("data.csv");
            try (InputStream inputStream = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null || headerLine.isBlank()) {
                    return loaded;
                }
                String[] headers = splitCsvLine(headerLine);
                Map<String, Integer> headerIndexes = new HashMap<>();
                for (int index = 0; index < headers.length; index++) {
                    headerIndexes.put(normalizeHeader(headers[index]), index);
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = splitCsvLine(line);
                    if (parts.length < 12) {
                        continue;
                    }
                    loaded.add(new CsvRecord(
                            getValue(parts, headerIndexes, "ind_definition"),
                            parseYear(getValue(parts, headerIndexes, "reportyear")),
                            getValue(parts, headerIndexes, "race_eth_name"),
                            getValue(parts, headerIndexes, "geotype"),
                            getValue(parts, headerIndexes, "geotypevalue"),
                            getValue(parts, headerIndexes, "geoname"),
                            getValue(parts, headerIndexes, "county_name"),
                            getValue(parts, headerIndexes, "region_name"),
                            getValue(parts, headerIndexes, "estimate"),
                            getValue(parts, headerIndexes, "numerator"),
                            getValue(parts, headerIndexes, "denominator"),
                            getValue(parts, headerIndexes, "version")
                    ));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load CSV data", e);
        }
        return loaded;
    }

    private String normalize(String input) {
        if (input == null) {
            return null;
        }
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String getValue(String[] parts, Map<String, Integer> headerIndexes, String headerName) {
        Integer index = headerIndexes.get(normalizeHeader(headerName));
        if (index == null || index >= parts.length) {
            return "";
        }
        return parts[index].trim();
    }

    private int parseYear(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int estimateValue(String value) {
        try {
            String normalized = value == null ? "0" : value.replace("%", "").trim();
            return (int) Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String[] splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values.toArray(String[]::new);
    }
}
