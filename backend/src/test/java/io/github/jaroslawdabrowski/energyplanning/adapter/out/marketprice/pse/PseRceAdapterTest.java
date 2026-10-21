package io.github.jaroslawdabrowski.energyplanning.adapter.out.marketprice.pse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/** Against a local server answering in PSE's shape - two pages, quarters ending at :15/:30/:45/:00. */
class PseRceAdapterTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void averagesEachHoursQuartersIntoZlotyPerKwhFollowingPages() throws IOException {
        start();
        var adapter = new PseRceAdapter(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort() + "/rce-pln");

        var prices = adapter.hourlyExportPrices(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15));

        // Hour 12: quarters ending 12:15, 12:30, 12:45, 13:00 -> (260 + 270 + 280 + 290) / 4 = 275 zł/MWh.
        assertThat(prices.get(LocalDateTime.of(2026, 9, 15, 12, 0))).isCloseTo(0.275, offset(1e-9));
        // The quarter ending at 12:00 belongs to hour 11.
        assertThat(prices.get(LocalDateTime.of(2026, 9, 15, 11, 0))).isCloseTo(0.4, offset(1e-9));
        assertThat(requests.get()).isEqualTo(2);
    }

    private void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rce-pln", exchange -> {
            int n = requests.incrementAndGet();
            String next = "http://127.0.0.1:" + server.getAddress().getPort() + "/rce-pln?page=2";
            String body = n == 1
                    ? "{\"value\":[" + row("12:00:00", 400) + "," + row("12:15:00", 260) + "," + row("12:30:00", 270)
                            + "],\"nextLink\":\"" + next + "\"}"
                    : "{\"value\":[" + row("12:45:00", 280) + "," + row("13:00:00", 290) + "]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private static String row(String time, double price) {
        return "{\"dtime\":\"2026-09-15 " + time + "\",\"rce_pln\":" + price + ",\"business_date\":\"2026-09-15\"}";
    }
}
