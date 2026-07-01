package com.example.educationstatisticscompiler;

public record CsvRecord(
        String indicator,
        int reportYear,
        String raceEthName,
        String geotype,
        String geotypeValue,
        String geoname,
        String countyName,
        String regionName,
        String estimate,
        String numerator,
        String denominator,
        String version
) {
}
