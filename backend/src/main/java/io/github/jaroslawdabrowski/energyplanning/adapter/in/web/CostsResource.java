package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.domain.DailyCost;
import io.github.jaroslawdabrowski.energyplanning.domain.MonthlyCosts;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetMonthlyCostsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/** {@code GET /api/costs?month=2026-10} - a month's electricity in money; this month when omitted. */
@Path("/api/costs")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class CostsResource {

    private final GetMonthlyCostsUseCase costs;
    private final ClockPort clockPort;

    public CostsResource(GetMonthlyCostsUseCase costs, ClockPort clockPort) {
        this.costs = costs;
        this.clockPort = clockPort;
    }

    @GET
    public MonthlyCostsResponse monthly(@QueryParam("month") String month) {
        YearMonth yearMonth;
        try {
            yearMonth = month == null || month.isBlank() ? YearMonth.from(clockPort.nowLocal()) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month must be YYYY-MM");
        }
        return MonthlyCostsResponse.from(costs.monthlyCosts(yearMonth));
    }

    public record MonthlyCostsResponse(
            String month,
            PricingResponse pricing,
            boolean marketPricesUnavailable,
            List<DayResponse> days,
            TotalsResponse totals) {

        static MonthlyCostsResponse from(MonthlyCosts m) {
            var days = m.days().stream().map(DayResponse::from).toList();
            var p = m.pricing();
            return new MonthlyCostsResponse(m.month().toString(),
                    new PricingResponse(p.cheapRateGross(), p.expensiveRateGross(), p.fixedMonthlyGross(), p.exportMultiplier()),
                    m.marketPricesUnavailable(), days, TotalsResponse.of(m.days()));
        }
    }

    public record PricingResponse(double cheapRateGross, double expensiveRateGross, double fixedMonthlyGross,
            double exportMultiplier) {
    }

    public record DayResponse(
            LocalDate date,
            double importCheapKwh,
            double importExpensiveKwh,
            double importCost,
            double exportKwh,
            double exportValue,
            double loadKwh,
            double baselineCost,
            double netCost,
            double savings,
            double coveredHours,
            boolean complete,
            boolean approximate,
            boolean exportPriceGaps) {

        static DayResponse from(DailyCost d) {
            return new DayResponse(d.date(), kwh(d.importCheapKwh()), kwh(d.importExpensiveKwh()), zl(d.importCost()),
                    kwh(d.exportKwh()), zl(d.exportValue()), kwh(d.loadKwh()), zl(d.baselineCost()), zl(d.netCost()),
                    zl(d.savings()), Math.round(d.coveredHours() * 10) / 10.0, d.complete(), d.approximate(), d.exportPriceGaps());
        }
    }

    /** Sums over the days with data - not the whole month, see {@code completeDays}/{@code days}. */
    public record TotalsResponse(
            double importCheapKwh,
            double importExpensiveKwh,
            double importCost,
            double exportKwh,
            double exportValue,
            double loadKwh,
            double baselineCost,
            double netCost,
            double savings,
            int days,
            int completeDays) {

        static TotalsResponse of(List<DailyCost> days) {
            double cheap = 0, expensive = 0, importCost = 0, export = 0, exportValue = 0, load = 0, baseline = 0;
            int complete = 0;
            for (var d : days) {
                cheap += d.importCheapKwh();
                expensive += d.importExpensiveKwh();
                importCost += d.importCost();
                export += d.exportKwh();
                exportValue += d.exportValue();
                load += d.loadKwh();
                baseline += d.baselineCost();
                complete += d.complete() ? 1 : 0;
            }
            return new TotalsResponse(kwh(cheap), kwh(expensive), zl(importCost), kwh(export), zl(exportValue),
                    kwh(load), zl(baseline), zl(importCost - exportValue), zl(baseline - (importCost - exportValue)),
                    days.size(), complete);
        }
    }

    private static double kwh(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double zl(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
