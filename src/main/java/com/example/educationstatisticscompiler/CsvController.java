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
    public String home(@RequestParam(value = "query", required = false) String query,
                       @RequestParam(value = "scope", required = false, defaultValue = "state") String scope,
                       @RequestParam(value = "location", required = false) String location,
                       @RequestParam(value = "race", required = false, defaultValue = "all") String race,
                       @RequestParam(value = "sortOrder", required = false, defaultValue = "highest") String sortOrder,
                       Model model) {
        List<CsvRecord> results = csvDataService.searchByFilters(scope, location, race, sortOrder, 20);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("scope", scope == null ? "state" : scope);
        model.addAttribute("location", location == null ? "" : location);
        model.addAttribute("race", race == null ? "all" : race);
        model.addAttribute("sortOrder", sortOrder == null ? "highest" : sortOrder);
        model.addAttribute("results", results);
        model.addAttribute("guidance", csvDataService.getGuidanceSuggestions());
        model.addAttribute("indicatorSummary", csvDataService.getIndicatorSummary());

        Map<String, String> explanation = new HashMap<>();
        explanation.put("title", "Education data explorer");
        explanation.put("description", "Choose a general area, race, and rate direction to explore education statistics in plain language.");
        model.addAttribute("explanation", explanation);
        return "index";
    }
}
