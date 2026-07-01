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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class CsvDataService {

    private static final Map<String, String> GEOTYPE_LABELS = Map.of(
            "CA", "State",
            "RE", "Region",
            "CO", "County",
            "PL", "City or community",
            "CT", "Census tract",
            "CD", "County subdivision"
    );

    private static final Map<String, String> REGION_CODE_NAMES = Map.ofEntries(
            Map.entry("01", "Bay Area"),
            Map.entry("02", "Butte"),
            Map.entry("03", "Central/Southeast Sierra"),
            Map.entry("04", "Monterey Bay"),
            Map.entry("05", "North Coast"),
            Map.entry("06", "Northeast Sierra"),
            Map.entry("07", "Northern Sacramento Valley"),
            Map.entry("08", "Sacramento Area"),
            Map.entry("09", "San Diego"),
            Map.entry("10", "San Joaquin Valley"),
            Map.entry("11", "San Luis Obispo"),
            Map.entry("12", "Santa Barbara"),
            Map.entry("13", "Shasta"),
            Map.entry("14", "Southern California")
    );

    private static final String DEFAULT_REPORT_PERIOD = "2006-2010";
    private static final List<String> KNOWN_REPORT_PERIODS = List.of("2011-2015", "2006-2010", "2000");

    private final List<CsvRecord> records;
    private final Map<String, String> dictionaryDefinitions;
    private final List<LocationOption> locationOptions;
    private final List<ReportPeriodOption> reportPeriodOptions;

    public CsvDataService() {
        this.dictionaryDefinitions = loadDictionaryDefinitions();
        this.records = loadRecords();
        this.locationOptions = buildLocationOptions();
        this.reportPeriodOptions = buildReportPeriodOptions();
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
                .sorted(Comparator.comparingInt((CsvRecord record) -> scoreRecord(record, terms)).reversed()
                        .thenComparing((CsvRecord record) -> estimateAsDouble(record), Comparator.reverseOrder()))
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
                .sorted(byEstimateHighestFirst())
                .limit(limit)
                .toList();
    }

    public List<ResultRow> searchByFilters(String location, String race, String period, int limit) {
        return toResultRows(filterRecords(location, race, period, limit), null);
    }

    public List<CsvRecord> searchByFilters(String scope, String location, String race, String sortOrder, int limit) {
        return filterRecords(resolveLegacyLocation(scope, location), race, DEFAULT_REPORT_PERIOD, limit);
    }

    private List<CsvRecord> filterRecords(String location, String race, String period, int limit) {
        return records.stream()
                .filter(this::hasMeaningfulEstimate)
                .filter(record -> matchesLocationFilter(record, location))
                .filter(record -> matchesRaceFilter(record, race))
                .filter(record -> matchesReportPeriodFilter(record, period))
                .sorted(byEstimateHighestFirst())
                .limit(limit)
                .toList();
    }

    private String resolveLegacyLocation(String scope, String location) {
        if (location == null || location.isBlank()) {
            return "state";
        }
        String normalizedScope = normalize(scope);
        if (normalizedScope != null && normalizedScope.contains("region")) {
            return "region:" + location.trim();
        }
        if (normalizedScope != null && normalizedScope.contains("county")) {
            return "county:" + location.trim();
        }
        if ("California".equalsIgnoreCase(location.trim())) {
            return "state";
        }
        return location.contains(":") ? location : "county:" + location.trim();
    }

    public CitySearchResult searchByCity(String cityQuery, String race, String period, int nearbyLimit) {
        String normalizedCity = normalize(cityQuery);
        if (normalizedCity == null || normalizedCity.isBlank()) {
            return CitySearchResult.empty();
        }

        List<CsvRecord> cityMatches = records.stream()
                .filter(this::hasMeaningfulEstimate)
                .filter(record -> "PL".equalsIgnoreCase(record.geotype()))
                .filter(record -> matchesRaceFilter(record, race))
                .filter(record -> matchesReportPeriodFilter(record, period))
                .filter(record -> matchesCityName(record, normalizedCity))
                .sorted(Comparator.comparingInt((CsvRecord record) -> cityMatchScore(record, normalizedCity)).reversed()
                        .thenComparing((CsvRecord record) -> estimateAsDouble(record), Comparator.reverseOrder()))
                .toList();

        if (cityMatches.isEmpty()) {
            return CitySearchResult.empty();
        }

        CsvRecord primary = cityMatches.getFirst();
        String county = primary.countyName();

        List<CsvRecord> nearby = records.stream()
                .filter(this::hasMeaningfulEstimate)
                .filter(record -> "PL".equalsIgnoreCase(record.geotype()))
                .filter(record -> matchesRaceFilter(record, race))
                .filter(record -> matchesReportPeriodFilter(record, period))
                .filter(record -> !record.geotypeValue().equals(primary.geotypeValue()))
                .filter(record -> county.equalsIgnoreCase(record.countyName()))
                .sorted(byEstimateHighestFirst())
                .limit(nearbyLimit)
                .toList();

        List<ResultRow> nearbyRows = new ArrayList<>();
        for (int index = 0; index < nearby.size(); index++) {
            nearbyRows.add(toResultRow(nearby.get(index), index + 2, false));
        }

        return new CitySearchResult(
                toResultRow(primary, 1, true),
                nearbyRows,
                county,
                primary.regionName(),
                true
        );
    }

    public List<LocationOption> getLocationOptions() {
        return locationOptions;
    }

    public List<String> getRaceOptions() {
        return List.of(
                "all|All races",
                "african|African American",
                "asian|Asian",
                "latino|Latino",
                "white|White",
                "aian|American Indian or Alaska Native",
                "nhopi|Native Hawaiian or Pacific Islander"
        );
    }

    public List<ReportPeriodOption> getReportPeriodOptions() {
        return reportPeriodOptions;
    }

    public String getDefaultReportPeriod() {
        return DEFAULT_REPORT_PERIOD;
    }

    public String formatReportPeriodLabel(String period) {
        return switch (period) {
            case "2000" -> "2000 (Decennial Census)";
            case "2006-2010" -> "2006–2010 (ACS 5-year average)";
            case "2011-2015" -> "2011–2015 (ACS 5-year average)";
            default -> period;
        };
    }

    public String getDisplayLocation(CsvRecord record) {
        String geotype = safe(record.geotype());
        String geoname = safe(record.geoname());
        String county = safe(record.countyName());
        String region = safe(record.regionName());

        return switch (geotype) {
            case "CA" -> "California";
            case "RE" -> region.isBlank() ? resolveRegionName(geoname, record.regionCode()) : region;
            case "CO" -> county.isBlank() ? geoname + " County" : county + " County";
            case "PL" -> formatPlaceName(geoname);
            case "CT" -> formatCensusTractName(geoname, county);
            case "CD" -> geoname.isBlank() ? "County subdivision" : geoname;
            default -> geoname.isBlank() ? "Unknown area" : geoname;
        };
    }

    public String getDisplayContext(CsvRecord record) {
        List<String> parts = new ArrayList<>();
        String geotypeLabel = GEOTYPE_LABELS.getOrDefault(safe(record.geotype()).toUpperCase(Locale.ROOT), "Area");
        parts.add(geotypeLabel);

        if (!safe(record.countyName()).isBlank() && !"CO".equalsIgnoreCase(record.geotype())) {
            parts.add(record.countyName() + " County");
        }
        if (!safe(record.regionName()).isBlank()) {
            parts.add(record.regionName());
        }
        return String.join(" · ", parts);
    }

    public String getDisplayRaceName(CsvRecord record) {
        return switch (safe(record.raceEthName())) {
            case "AfricanAm" -> "African American";
            case "AIAN" -> "American Indian or Alaska Native";
            case "NHOPI" -> "Native Hawaiian or Pacific Islander";
            case "Total" -> "All races (Total)";
            default -> record.raceEthName();
        };
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
                "Pick one reporting period to compare areas fairly",
                "Search by city: try Pomona, Fresno, or Oakland",
                "Compare counties in the Bay Area region",
                "View Latino education rates for 2011–2015"
        );
    }

    public String getIndicatorSummary() {
        String definition = dictionaryDefinitions.getOrDefault("ind_definition", "");
        if (definition.isBlank()) {
            return "This dataset shows the share of adults age 25 and up with a four-year college degree or higher.";
        }
        return definition + " Results are sorted from highest to lowest so you can quickly spot the strongest areas.";
    }

    private List<ResultRow> toResultRows(List<CsvRecord> filtered, String highlightedGeotypeValue) {
        List<ResultRow> rows = new ArrayList<>();
        for (int index = 0; index < filtered.size(); index++) {
            CsvRecord record = filtered.get(index);
            boolean highlighted = highlightedGeotypeValue != null
                    && highlightedGeotypeValue.equals(record.geotypeValue());
            rows.add(toResultRow(record, index + 1, highlighted));
        }
        return rows;
    }

    private ResultRow toResultRow(CsvRecord record, int rank, boolean highlighted) {
        return new ResultRow(
                getDisplayLocation(record),
                getDisplayContext(record),
                getDisplayRaceName(record),
                record.estimate(),
                record.reportYear(),
                rank,
                highlighted
        );
    }

    private List<LocationOption> buildLocationOptions() {
        List<LocationOption> options = new ArrayList<>();
        options.add(new LocationOption("state", "All of California (by county)", "Overview"));

        Set<String> regions = records.stream()
                .map(CsvRecord::regionName)
                .filter(name -> name != null && !name.isBlank() && !name.equalsIgnoreCase("NA"))
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        for (String region : regions) {
            options.add(new LocationOption("region:" + region, region, "Regions"));
        }

        Set<String> counties = records.stream()
                .map(CsvRecord::countyName)
                .filter(name -> name != null && !name.isBlank() && !name.equalsIgnoreCase("NA"))
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        for (String county : counties) {
            options.add(new LocationOption("county:" + county, county + " County (cities & communities)", "Counties"));
        }

        return options;
    }

    private List<ReportPeriodOption> buildReportPeriodOptions() {
        LinkedHashSet<String> periods = new LinkedHashSet<>(KNOWN_REPORT_PERIODS);
        records.stream()
                .map(CsvRecord::reportYear)
                .filter(year -> year != null && !year.isBlank() && !year.equalsIgnoreCase("NA"))
                .forEach(periods::add);

        return periods.stream()
                .sorted(Comparator.reverseOrder())
                .map(period -> new ReportPeriodOption(period, formatReportPeriodLabel(period)))
                .toList();
    }

    private boolean matchesReportPeriodFilter(CsvRecord record, String period) {
        String effectivePeriod = (period == null || period.isBlank()) ? DEFAULT_REPORT_PERIOD : period.trim();
        return effectivePeriod.equalsIgnoreCase(safe(record.reportYear()));
    }

    private boolean matchesLocationFilter(CsvRecord record, String location) {
        if (location == null || location.isBlank() || location.equalsIgnoreCase("state")) {
            return "CO".equalsIgnoreCase(record.geotype());
        }

        if (location.startsWith("region:")) {
            String region = location.substring("region:".length()).trim();
            return "CO".equalsIgnoreCase(record.geotype())
                    && safe(record.regionName()).equalsIgnoreCase(region);
        }

        if (location.startsWith("county:")) {
            String county = location.substring("county:".length()).trim();
            return "PL".equalsIgnoreCase(record.geotype())
                    && safe(record.countyName()).equalsIgnoreCase(county);
        }

        String normalizedLocation = normalize(location);
        String searchableLocation = String.join(" ",
                getDisplayLocation(record),
                record.countyName(),
                record.regionName()
        ).toLowerCase(Locale.ROOT);
        return searchableLocation.contains(normalizedLocation);
    }

    private boolean matchesRaceFilter(CsvRecord record, String race) {
        String normalizedRace = normalize(race);
        if (normalizedRace == null || normalizedRace.isBlank() || normalizedRace.contains("all")) {
            return "Total".equalsIgnoreCase(record.raceEthName());
        }

        String searchableRace = record.raceEthName().toLowerCase(Locale.ROOT);
        return switch (normalizedRace) {
            case "african", "black", "africanam" -> searchableRace.contains("african");
            case "latino", "hispanic" -> searchableRace.contains("latino");
            case "asian" -> searchableRace.contains("asian");
            case "white" -> searchableRace.contains("white");
            case "aian", "native", "indian" -> searchableRace.contains("aian") || searchableRace.contains("indian");
            case "nhopi", "pacific" -> searchableRace.contains("nhopi") || searchableRace.contains("pacific");
            default -> searchableRace.contains(normalizedRace);
        };
    }

    private boolean matchesCityName(CsvRecord record, String normalizedCity) {
        String placeName = normalizePlaceName(record.geoname());
        if (placeName.contains(normalizedCity) || normalizedCity.contains(placeName)) {
            return true;
        }

        String[] queryParts = normalizedCity.split("\\s+");
        return Arrays.stream(queryParts)
                .filter(part -> part.length() > 2)
                .allMatch(placeName::contains);
    }

    private int cityMatchScore(CsvRecord record, String normalizedCity) {
        String placeName = normalizePlaceName(record.geoname());
        if (placeName.equals(normalizedCity)) {
            return 100;
        }
        if (placeName.startsWith(normalizedCity)) {
            return 80;
        }
        if (placeName.contains(normalizedCity)) {
            return 60;
        }
        return 10;
    }

    private String formatPlaceName(String geoname) {
        if (geoname.isBlank()) {
            return "Unknown city or community";
        }
        return geoname.replace(" CDP", " (community)")
                .replace(" city", " (city)")
                .replace(" town", " (town)");
    }

    private String formatCensusTractName(String tractNumber, String county) {
        String tractLabel = tractNumber.isBlank() ? "Unknown census tract" : "Census Tract " + tractNumber;
        if (county.isBlank()) {
            return tractLabel;
        }
        return tractLabel + ", " + county + " County";
    }

    private String resolveRegionName(String geoname, String regionCode) {
        if (!geoname.isBlank()) {
            return geoname;
        }
        return REGION_CODE_NAMES.getOrDefault(safe(regionCode), "Unknown region");
    }

    private String normalizePlaceName(String geoname) {
        return normalize(geoname)
                .replace(" cdp", "")
                .replace(" city", "")
                .replace(" town", "")
                .trim();
    }

    private Comparator<CsvRecord> byEstimateHighestFirst() {
        return Comparator.comparingDouble((CsvRecord record) -> estimateAsDouble(record)).reversed();
    }

    private double estimateAsDouble(CsvRecord record) {
        return estimateAsDouble(record.estimate());
    }

    private double estimateAsDouble(String value) {
        try {
            String normalized = value == null ? "0" : value.replace("%", "").trim();
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean matchesQuery(CsvRecord record, String[] terms) {
        if (terms.length == 0) {
            return true;
        }

        String searchable = buildSearchableText(record).toLowerCase(Locale.ROOT);

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
        String searchable = buildSearchableText(record).toLowerCase(Locale.ROOT);

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

    private String buildSearchableText(CsvRecord record) {
        return String.join(" ",
                record.indicator(),
                record.raceEthName(),
                getDisplayRaceName(record),
                getDisplayLocation(record),
                getDisplayContext(record),
                record.countyName(),
                record.regionName(),
                record.estimate(),
                record.reportYear()
        );
    }

    private boolean hasMeaningfulEstimate(CsvRecord record) {
        String estimate = record.estimate();
        if (estimate == null || estimate.isBlank()) {
            return false;
        }
        String normalizedEstimate = estimate.trim();
        return !normalizedEstimate.equalsIgnoreCase("NA")
                && !normalizedEstimate.equalsIgnoreCase("N/A")
                && !normalizedEstimate.equalsIgnoreCase("null");
    }

    private boolean matchesPlaceFilter(CsvRecord record, String place, String county) {
        if ((place == null || place.isBlank()) && (county == null || county.isBlank())) {
            return true;
        }

        String searchableLocation = buildSearchableText(record).toLowerCase(Locale.ROOT);

        boolean placeMatch = place != null && !place.isBlank() && searchableLocation.contains(place);
        boolean countyMatch = county != null && !county.isBlank() && searchableLocation.contains(county);
        return placeMatch || countyMatch;
    }

    private boolean matchesTopicFilter(CsvRecord record, String topic) {
        if (topic == null || topic.isBlank()) {
            return true;
        }
        String searchableTopic = String.join(" ", record.indicator(), record.raceEthName(), record.geotype()).toLowerCase(Locale.ROOT);
        return searchableTopic.contains(topic);
    }

    private boolean containsLocationField(String term, CsvRecord record) {
        return buildSearchableText(record).toLowerCase(Locale.ROOT).contains(term);
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

    private String safe(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NA")) {
            return "";
        }
        return value.trim();
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
                    if (parts.length >= 2 && !parts[0].trim().startsWith("NOTE")) {
                        String value = parts.length >= 4 && !parts[3].trim().isBlank()
                                ? parts[3].trim()
                                : parts[1].trim();
                        definitions.put(parts[0].trim(), value);
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
                    if (!isAggregateCsvRow(parts, headerIndexes)) {
                        continue;
                    }
                    loaded.add(new CsvRecord(
                            getValue(parts, headerIndexes, "ind_definition"),
                            getValue(parts, headerIndexes, "reportyear"),
                            getValue(parts, headerIndexes, "race_eth_name"),
                            getValue(parts, headerIndexes, "geotype"),
                            getValue(parts, headerIndexes, "geotypevalue"),
                            getValue(parts, headerIndexes, "geoname"),
                            getValue(parts, headerIndexes, "county_name"),
                            getValue(parts, headerIndexes, "county_fips"),
                            getValue(parts, headerIndexes, "region_name"),
                            getValue(parts, headerIndexes, "region_code"),
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

    private boolean isAggregateCsvRow(String[] parts, Map<String, Integer> headerIndexes) {
        String strataOne = getValue(parts, headerIndexes, "strata_one_code");
        String strataTwo = getValue(parts, headerIndexes, "strata_two_code");
        return isMissing(strataOne) && isMissing(strataTwo);
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("NA");
    }

    private String normalize(String input) {
        if (input == null) {
            return null;
        }
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
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
