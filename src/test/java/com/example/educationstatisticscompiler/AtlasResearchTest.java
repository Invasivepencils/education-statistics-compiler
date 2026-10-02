package com.example.educationstatisticscompiler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasResearchTest {
    private final CsvDataService service = new CsvDataService();
    @Test void keepsGeographiesAndPeriodsSeparate() {
        var counties = service.explore("", "county", "all", "2011-2015", "highest");
        assertEquals(58, counties.size());
        assertTrue(counties.stream().allMatch(r -> r.geotype().equals("CO") && r.reportYear().equals("2011-2015")));
        var cities = service.explore("Los Angeles", "city", "all", "2011-2015", "highest");
        assertFalse(cities.isEmpty());
        assertTrue(cities.stream().allMatch(r -> r.geotype().equals("PL")));
    }
    @Test void searchNarrowsCountyResults() {
        var results = service.explore("Los Angeles County", "county", "all", "2011-2015", "highest");
        assertEquals(1, results.size());
        assertEquals("Los Angeles", results.getFirst().countyName());
        assertTrue(service.explore("zzzz-no-place", "county", "all", "2011-2015", "highest").isEmpty());
    }
    @Test void sortsDecimalRatesInBothDirections() {
        for (String order : new String[]{"highest","lowest"}) {
            var rows = service.explore("", "county", "all", "2011-2015", order);
            for (int i=1;i<rows.size();i++) {
                double previous=service.rate(rows.get(i-1)), current=service.rate(rows.get(i));
                assertTrue(order.equals("highest") ? previous>=current : previous<=current);
            }
        }
    }
    @Test void statewideBenchmarkMatchesGroupAndPeriod() {
        var state = service.explore("", "state", "latino", "2006-2010", "highest");
        assertFalse(state.isEmpty());
        assertEquals(service.rate(state.getFirst()), service.benchmark("latino","2006-2010"));
    }
}
