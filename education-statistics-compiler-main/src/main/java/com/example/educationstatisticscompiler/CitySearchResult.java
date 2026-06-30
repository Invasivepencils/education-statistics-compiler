package com.example.educationstatisticscompiler;

import java.util.List;

public record CitySearchResult(
        ResultRow matchedPlace,
        List<ResultRow> nearbyPlaces,
        String countyName,
        String regionName,
        boolean found
) {
    public static CitySearchResult empty() {
        return new CitySearchResult(null, List.of(), "", "", false);
    }
}
