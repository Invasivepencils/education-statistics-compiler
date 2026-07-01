package com.example.educationstatisticscompiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CsvController {

    private final CsvDataService csvDataService;

    public CsvController(CsvDataService csvDataService) {
        this.csvDataService = csvDataService;
    }

    @GetMapping("/")
    public String home(@RequestParam(value = "location", required = false, defaultValue = "state") String location,
                       @RequestParam(value = "race", required = false, defaultValue = "all") String race,
                       @RequestParam(value = "period", required = false, defaultValue = "2006-2010") String period,
                       @RequestParam(value = "city", required = false) String city,
                       Model model) {
        String selectedPeriod = (period == null || period.isBlank())
                ? csvDataService.getDefaultReportPeriod()
                : period;

        List<ResultRow> results = csvDataService.searchByFilters(location, race, selectedPeriod, 25);
        CitySearchResult citySearch = csvDataService.searchByCity(city, race, selectedPeriod, 12);

        model.addAttribute("location", location);
        model.addAttribute("race", race);
        model.addAttribute("period", selectedPeriod);
        model.addAttribute("city", city == null ? "" : city);
        model.addAttribute("results", results);
        model.addAttribute("citySearch", citySearch);
        model.addAttribute("locationOptions", csvDataService.getLocationOptions());
        model.addAttribute("raceOptions", csvDataService.getRaceOptions());
        model.addAttribute("reportPeriodOptions", csvDataService.getReportPeriodOptions());
        model.addAttribute("selectedPeriodLabel", csvDataService.formatReportPeriodLabel(selectedPeriod));
        model.addAttribute("guidance", csvDataService.getGuidanceSuggestions());
        model.addAttribute("indicatorSummary", csvDataService.getIndicatorSummary());

        Map<String, String> explanation = new HashMap<>();
        explanation.put("title", "Education data explorer");
        explanation.put("description", "Choose an area and race group to explore education rates ranked from highest to lowest.");
        model.addAttribute("explanation", explanation);
        return "index";
    }
}
