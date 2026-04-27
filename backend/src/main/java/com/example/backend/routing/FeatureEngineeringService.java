package com.example.backend.routing;

import com.example.backend.dto.RouteFeaturesDto;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class FeatureEngineeringService {

    public RouteFeaturesDto deriveFeatures(RouteComputation route, String selectedDepartureTime) {
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of("Europe/Copenhagen"));
        LocalTime departureTime = normalizeDepartureTime(selectedDepartureTime, now.toLocalTime());
        int departureHour = departureTime.getHour();

        double distanceKm = route.distanceKm();
        double durationMin = route.durationMinutes();
        double avgSpeed = durationMin > 0 ? distanceKm / (durationMin / 60.0) : 0;

        double citySignal = citySignal(route.origin().displayName(), route.destination().displayName());
        double urbanDensity = clamp(citySignal + (distanceKm < 20 ? 0.18 : 0.0) - (distanceKm > 120 ? 0.12 : 0.0));
        String regionType = regionType(urbanDensity, distanceKm);
        String timeOfDay = timeOfDay(departureHour);
        String season = season(now.getMonth());
        String dayOfWeek = now.getDayOfWeek().name().toLowerCase(Locale.ROOT);
        double departurePressure = departurePressure(departureHour);

        double roadTypeScore = clamp((avgSpeed / 110.0) + (distanceKm > 60 ? 0.12 : 0.0) - urbanDensity * 0.22);
        double trafficLevel = clamp(
                0.18
                        + urbanDensity * 0.5
                        + peakHourBoost(timeOfDay)
                        + departurePressure
                        + weekdayBoost(now.getDayOfWeek())
        );
        double weatherRisk = clamp(baseWeatherRisk(season) + deterministicNoise(route, 0.18));
        double rainfallMm = Math.max(0.0, rounded((weatherRisk * 11.0) + deterministicNoise(route, 3.5)));
        double humidity = clamp(0.48 + weatherRisk * 0.4 + deterministicNoise(route, 0.08));
        double windSpeedKmh = Math.max(5.0, rounded(12 + seasonalWindBoost(season) + deterministicNoise(route, 8.0)));
        double temperatureC = rounded(seasonalTemperature(season) + deterministicNoise(route, 5.0));
        double constructionRisk = clamp(
                0.14 + urbanDensity * 0.2 + summerConstructionBoost(season) + deterministicNoise(route, 0.14)
        );
        double elevationVariation = Math.max(2.0, rounded(5 + distanceKm * 0.08 + deterministicNoise(route, 8.0)));
        double accidentRisk = clamp(
                0.14
                        + trafficLevel * 0.38
                        + weatherRisk * 0.24
                        + departurePressure * 0.22
                        + (avgSpeed > 95 ? 0.1 : 0.0)
        );
        double vehicleLoad = clamp(
                0.16
                        + trafficLevel * 0.46
                        + departurePressure * 0.2
                        + (distanceKm > 150 ? 0.1 : 0.0)
                        + deterministicNoise(route, 0.1)
        );

        return new RouteFeaturesDto(
                rounded(distanceKm),
                rounded(durationMin),
                rounded(avgSpeed),
                rounded(roadTypeScore),
                rounded(trafficLevel),
                rounded(weatherRisk),
                rounded(rainfallMm),
                timeOfDay,
                dayOfWeek,
                rounded(urbanDensity),
                rounded(accidentRisk),
                rounded(constructionRisk),
                rounded(elevationVariation),
                rounded(temperatureC),
                rounded(humidity),
                rounded(windSpeedKmh),
                season,
                regionType,
                rounded(vehicleLoad)
        );
    }

    private double citySignal(String originLabel, String destinationLabel) {
        String combined = (originLabel + " " + destinationLabel).toLowerCase(Locale.ROOT);
        if (combined.contains("copenhagen") || combined.contains("kobenhavn")) {
            return 0.82;
        }
        if (combined.contains("aarhus") || combined.contains("odense") || combined.contains("aalborg")) {
            return 0.66;
        }
        if (combined.contains("roskilde") || combined.contains("vejle") || combined.contains("esbjerg")) {
            return 0.52;
        }
        return 0.34;
    }

    private String regionType(double urbanDensity, double distanceKm) {
        if (urbanDensity > 0.68 || distanceKm < 22) {
            return "urban";
        }
        if (urbanDensity > 0.44 || distanceKm < 80) {
            return "suburban";
        }
        return "regional";
    }

    private String timeOfDay(int hour) {
        if (hour < 6) {
            return "early_morning";
        }
        if (hour < 10) {
            return "morning_peak";
        }
        if (hour < 15) {
            return "midday";
        }
        if (hour < 19) {
            return "afternoon_peak";
        }
        if (hour < 23) {
            return "evening";
        }
        return "night";
    }

    private LocalTime normalizeDepartureTime(String selectedDepartureTime, LocalTime fallbackTime) {
        if (selectedDepartureTime == null || selectedDepartureTime.isBlank()) {
            return fallbackTime;
        }

        try {
            return LocalTime.parse(selectedDepartureTime);
        } catch (DateTimeParseException exception) {
            return fallbackTime;
        }
    }

    private double departurePressure(int hour) {
        return switch (hour) {
            case 7, 8, 15, 16 -> 0.22;
            case 9, 17, 18 -> 0.16;
            case 6, 10, 14, 19 -> 0.08;
            case 11, 12, 13, 20 -> 0.02;
            case 22, 23, 0, 1, 2, 3, 4, 5 -> -0.12;
            default -> -0.04;
        };
    }

    private String season(Month month) {
        return switch (month) {
            case DECEMBER, JANUARY, FEBRUARY -> "winter";
            case MARCH, APRIL, MAY -> "spring";
            case JUNE, JULY, AUGUST -> "summer";
            default -> "autumn";
        };
    }

    private double peakHourBoost(String timeOfDay) {
        return switch (timeOfDay) {
            case "morning_peak", "afternoon_peak" -> 0.22;
            case "midday" -> 0.04;
            case "evening" -> -0.01;
            case "early_morning", "night" -> -0.12;
            default -> 0.0;
        };
    }

    private double weekdayBoost(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SATURDAY -> -0.02;
            case SUNDAY -> -0.07;
            default -> 0.08;
        };
    }

    private double baseWeatherRisk(String season) {
        return switch (season) {
            case "winter" -> 0.62;
            case "autumn" -> 0.48;
            case "spring" -> 0.32;
            default -> 0.2;
        };
    }

    private double seasonalWindBoost(String season) {
        return switch (season) {
            case "winter" -> 14;
            case "autumn" -> 10;
            case "spring" -> 6;
            default -> 4;
        };
    }

    private double seasonalTemperature(String season) {
        return switch (season) {
            case "winter" -> 1.5;
            case "spring" -> 9.0;
            case "summer" -> 18.0;
            default -> 10.0;
        };
    }

    private double summerConstructionBoost(String season) {
        return "summer".equals(season) ? 0.18 : 0.05;
    }

    private double deterministicNoise(RouteComputation route, double amplitude) {
        long seed = Math.abs((route.origin().displayName() + route.destination().displayName()).hashCode());
        double normalized = ((seed % 1000) / 999.0) - 0.5;
        return normalized * amplitude;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
