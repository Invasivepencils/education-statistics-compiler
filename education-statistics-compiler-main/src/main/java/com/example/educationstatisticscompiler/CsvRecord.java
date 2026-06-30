package com.example.educationstatisticscompiler;

public record CsvRecord(
        String indicator,
        String reportYear,
        String raceEthName,
        String geotype,
        String geotypeValue,
        String geoname,
        String countyName,
        String countyFips,
        String regionName,
        String regionCode,
        String estimate,
        String numerator,
        String denominator,
        String version
) {
}
