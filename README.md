# Attainment Atlas

**[Open the browser demo →](https://invasivepencils.github.io/education-statistics-compiler/)** · No installation or sign-in required.


**How does college-degree attainment differ across California communities?**

A Spring Boot research app that turns the California Department of Public Health's educational-attainment snapshot into searchable, comparable evidence. Built for civic researchers exploring geographic and demographic patterns among adults age 25 and older.

## Features

- Search places and their county/region context, with suggestions and starter queries.
- Compare counties, cities/communities, regions, or statewide estimates separately.
- Hold population group and reporting period consistent; compare against the matching California benchmark.
- Sort real decimal percentages, paginate results, compare two areas, and export the complete filtered CSV.
- Responsive interface with accessible labels, clear empty states, and source documentation.

## Run locally

Requires Java 25. Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

On macOS/Linux: `./gradlew test` and `./gradlew bootRun`. Open http://localhost:8080.

The root `src/` is the active application. The pre-existing `education-statistics-compiler-main/` directory and archive are legacy copies, not required to run this version.

## Data and interpretation

The bundled dataset contains historical estimates for 2000, 2006–2010, and 2011–2015. The measure is the percentage of adults 25+ with a four-year college degree or higher. ACS five-year estimates describe a period, not an annual observation. The 2000 Census uses a different collection method.

Only unstratified rows with finite percentages between 0 and 100 are shown. Geographic levels are never mixed in a ranking, and overlapping areas are not averaged. Missing estimates are excluded. Groups other than Latino and Total use the source's non-Latino definitions.

Differences are descriptive: the app does not estimate significance, explain causes, measure school quality, or evaluate individuals. The snapshot ends in 2015 and should not be described as current data.

Sources: [CDPH dataset catalog](https://catalog.data.gov/dataset/educational-attainment) and [indicator narrative](https://www.cdph.ca.gov/Programs/OHE/CDPH%20Document%20Library/HCI/ADA%20Compliant%20Documents/HCI_EducationaAttainment_Narrative_355_8-8-17-ADA.pdf).

## Engineering

Java / Spring Boot / Thymeleaf, with lightweight CSS and JavaScript. Filtering runs on the server; URLs preserve research queries, and CSV export uses the same filter pipeline. Regression tests cover geography, historical periods, query narrowing, and decimal ranking.
