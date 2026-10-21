package io.github.jaroslawdabrowski.energyplanning.adapter.out.marketprice.pse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jaroslawdabrowski.energyplanning.port.out.MarketPricePort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The market price (RCE) from PSE's public report API, {@code /api/rce-pln}: one row per 15
 * minutes in PLN/MWh, {@code dtime} being the END of the quarter in local time. There's no
 * endpoint for the monthly RCEm (checked against PSE's EndpointsMap.pdf) - and the invoice
 * doesn't use a plain monthly mean anyway, it weights hourly RCE by when energy was exported.
 * So this gives hourly prices: the mean of each hour's four quarters, in zł/kWh.
 *
 * A day's prices never change once published (day-ahead), so complete days are cached for the
 * life of the process. Note: macOS curl doesn't trust this host's certificate chain; Java does.
 */
@ApplicationScoped
public class PseRceAdapter implements MarketPricePort {

    private static final DateTimeFormatter DTIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 92 on the spring DST day, 100 on the autumn one, 96 otherwise - fewer means not fully published. */
    private static final int MIN_QUARTERS_FOR_A_COMPLETE_DAY = 92;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Map<LocalDate, Map<LocalDateTime, Double>> completeDays = new ConcurrentHashMap<>();

    public PseRceAdapter(ObjectMapper objectMapper,
            @ConfigProperty(name = "pvopt.market-price.pse-url", defaultValue = "https://api.raporty.pse.pl/api/rce-pln")
            String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public Map<LocalDateTime, Double> hourlyExportPrices(LocalDate from, LocalDate to) {
        var result = new TreeMap<LocalDateTime, Double>();
        var missing = new ArrayList<LocalDate>();
        for (var day = from; !day.isAfter(to); day = day.plusDays(1)) {
            var cached = completeDays.get(day);
            if (cached != null) {
                result.putAll(cached);
            } else {
                missing.add(day);
            }
        }
        if (!missing.isEmpty()) {
            result.putAll(fetch(missing.getFirst(), missing.getLast()));
        }
        return result;
    }

    private Map<LocalDateTime, Double> fetch(LocalDate from, LocalDate to) {
        Map<LocalDateTime, List<Double>> quarters = new HashMap<>();
        Map<LocalDate, Integer> quartersPerDay = new HashMap<>();
        String filter = "business_date ge '" + from + "' and business_date le '" + to + "'";
        String url = baseUrl + "?$filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8).replace("+", "%20");
        try {
            while (url != null) {
                var response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("PSE answered " + response.statusCode() + " for " + url);
                }
                JsonNode body = objectMapper.readTree(response.body());
                for (JsonNode row : body.path("value")) {
                    var quarterEnd = LocalDateTime.parse(row.path("dtime").asText(), DTIME);
                    var hour = quarterEnd.minusMinutes(15).truncatedTo(ChronoUnit.HOURS);
                    quarters.computeIfAbsent(hour, h -> new ArrayList<>()).add(row.path("rce_pln").asDouble());
                    quartersPerDay.merge(LocalDate.parse(row.path("business_date").asText()), 1, Integer::sum);
                }
                url = body.hasNonNull("nextLink") ? body.path("nextLink").asText() : null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read market prices from PSE", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading market prices from PSE", e);
        }
        var hourly = new TreeMap<LocalDateTime, Double>();
        quarters.forEach((hour, values) ->
                hourly.put(hour, values.stream().mapToDouble(Double::doubleValue).average().orElseThrow() / 1000.0));
        quartersPerDay.forEach((day, count) -> {
            if (count >= MIN_QUARTERS_FOR_A_COMPLETE_DAY) {
                var dayPrices = new TreeMap<LocalDateTime, Double>();
                hourly.forEach((hour, price) -> {
                    if (hour.toLocalDate().equals(day)) {
                        dayPrices.put(hour, price);
                    }
                });
                completeDays.put(day, Map.copyOf(dayPrices));
            }
        });
        return hourly;
    }
}
