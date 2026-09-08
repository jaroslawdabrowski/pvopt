package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Which of the two daily grid-charge decisions this is. G12's cheap tariff has exactly
 * two windows - the overnight one (22:00-06:00) and the short afternoon one (13:00-15:00)
 * - so a decision is made once for each, not hourly. The adapter maps a window to whatever
 * concrete inverter TOU slot(s) it corresponds to; the domain only needs to know which
 * window a decision/schedule belongs to.
 */
public enum ChargeWindow {
    OVERNIGHT,
    AFTERNOON
}
