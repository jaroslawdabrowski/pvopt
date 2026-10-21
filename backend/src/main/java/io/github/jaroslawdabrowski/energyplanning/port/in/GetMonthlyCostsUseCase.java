package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.MonthlyCosts;

import java.time.YearMonth;

public interface GetMonthlyCostsUseCase {

    MonthlyCosts monthlyCosts(YearMonth month);
}
