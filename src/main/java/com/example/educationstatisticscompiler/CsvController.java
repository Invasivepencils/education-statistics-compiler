package com.example.educationstatisticscompiler;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

@Controller
public class CsvController {
    private final CsvDataService service;
    public CsvController(CsvDataService service) { this.service = service; }

    @GetMapping("/")
    public String home(@RequestParam(defaultValue="") String query,
        @RequestParam(defaultValue="county") String scope,
        @RequestParam(defaultValue="all") String race,
        @RequestParam(defaultValue="2011-2015") String period,
        @RequestParam(defaultValue="highest") String sortOrder,
        @RequestParam(defaultValue="1") int page, Model model) {
        scope = Set.of("county", "city", "region", "state").contains(scope) ? scope : "county";
        period = Set.of("2000", "2006-2010", "2011-2015").contains(period) ? period : "2011-2015";
        List<CsvRecord> all = service.explore(query, scope, race, period, sortOrder);
        int pages = Math.max(1, (all.size() + 24) / 25);
        page = Math.max(1, Math.min(page, pages));
        model.addAttribute("results", all.subList((page-1)*25, Math.min(page*25, all.size())));
        model.addAttribute("query", query); model.addAttribute("scope", scope);
        model.addAttribute("race", race); model.addAttribute("period", period);
        model.addAttribute("sortOrder", sortOrder); model.addAttribute("page", page);
        model.addAttribute("pages", pages); model.addAttribute("total", all.size());
        model.addAttribute("benchmark", service.benchmark(race, period));
        model.addAttribute("service", service); model.addAttribute("suggestions", service.placeSuggestions());
        return "index";
    }

    @GetMapping(value="/export", produces="text/csv")
    public ResponseEntity<String> export(@RequestParam(defaultValue="") String query,
        @RequestParam(defaultValue="county") String scope, @RequestParam(defaultValue="all") String race,
        @RequestParam(defaultValue="2011-2015") String period, @RequestParam(defaultValue="highest") String sortOrder) {
        StringBuilder csv = new StringBuilder("place,geographic_level,group,reporting_period,attainment_percent,numerator,denominator\r\n");
        for (CsvRecord r : service.explore(query, scope, race, period, sortOrder)) {
            csv.append(List.of(service.getDisplayLocation(r), r.geotype(), service.getDisplayRaceName(r),
                r.reportYear(), r.estimate(), r.numerator(), r.denominator()).stream()
                .map(CsvController::escape).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=attainment.csv").body(csv.toString());
    }
    private static String escape(String text) {
        if (text.matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
