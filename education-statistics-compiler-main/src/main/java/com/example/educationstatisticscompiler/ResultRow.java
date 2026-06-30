package com.example.educationstatisticscompiler;

public record ResultRow(
        String displayLocation,
        String displayContext,
        String raceEthName,
        String estimate,
        String reportYear,
        int rank,
        boolean highlighted
) {
}
