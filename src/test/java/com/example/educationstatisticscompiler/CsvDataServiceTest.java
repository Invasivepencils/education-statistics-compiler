package com.example.educationstatisticscompiler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CsvDataServiceTest {

    private final CsvDataService service = new CsvDataService();

    @Test
    void findsRelevantRowsForLocationAndRaceQuery() {
        List<CsvRecord> results = service.search("African American in Los Angeles County");

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(record ->
                record.raceEthName().contains("African") && record.countyName().contains("Los Angeles")
        ));
    }

    @Test
    void providesPlainLanguageGuidanceSuggestions() {
        List<String> suggestions = service.getGuidanceSuggestions();

        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.stream().anyMatch(suggestion -> suggestion.contains("African")));
    }

    @Test
    void filtersToRegionResultsAndSkipsMeaninglessEstimates() {
        List<CsvRecord> results = service.searchByFilters("region", "Bay Area", "all", "highest", 10);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(record -> record.regionName().contains("Bay Area")));
        assertTrue(results.stream().noneMatch(record ->
                record.estimate() == null || record.estimate().isBlank() || record.estimate().equalsIgnoreCase("NA")
        ));
    }

    @Test
    void findsCountySpecificRowsAndSupportsRanking() {
        List<CsvRecord> results = service.searchByPlace("Los Angeles", "", "college", "highest", 5);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(record ->
                record.countyName().contains("Los Angeles") || record.geoname().contains("Los Angeles")
        ));
    }

    @Test
    void explainsWhenARequestedMeasureIsNotAvailable() {
        String availability = service.getMeasureAvailability("high-school");

        assertTrue(availability.contains("not currently available"));
    }
}
