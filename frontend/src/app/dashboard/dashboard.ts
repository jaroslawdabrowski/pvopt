import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ChargeDecisionDto, ChargeWindow, EnergyPlanningApi, EnergyStatusDto } from '../core/energy-planning-api.service';
import { ProcessSettingsApi, ProcessSettingsDto } from '../core/process-settings-api.service';
import { settingsAwareErrorKey } from '../core/settings-error.util';
import { LanguageService } from '../core/language.service';
import { decisionPeriod, decisionReserve, decisionVerdict, formatDayAndTime, formatTime } from '../core/format';
import { DayDial, DecisionMark, ReserveMark } from './day-dial';
import { WindowTone, hoursOf, nextOccurrence, parseTariffWindows } from './tariff-windows';

const DAY_MS = 24 * 60 * 60 * 1000;
const STALE_AFTER_MS = 26 * 60 * 60 * 1000;

interface UpcomingDecision {
  window: ChargeWindow;
  /** The overnight decision runs twice: before the window and again inside it. */
  recheck: boolean;
  at: Date;
  tomorrow: boolean;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, TranslatePipe, DayDial],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit, OnDestroy {
  private readonly api = inject(EnergyPlanningApi);
  private readonly settingsApi = inject(ProcessSettingsApi);
  private readonly language = inject(LanguageService).currentLanguage;

  readonly status = signal<EnergyStatusDto | null>(null);
  readonly settings = signal<ProcessSettingsDto | null>(null);
  readonly loading = signal(true);
  readonly runningOvernight = signal(false);
  readonly runningAfternoon = signal(false);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);
  readonly now = signal(new Date());
  private clock?: ReturnType<typeof setInterval>;

  /**
   * A problem from the last day that nothing has fixed since: a decision for the same window
   * made after it, with a forecast, means it's resolved.
   */
  readonly recentIssue = computed(() => {
    const issue = this.status()?.latestIssue;
    if (!issue || this.now().getTime() - new Date(issue.occurredAt).getTime() >= DAY_MS) return null;
    const later = this.latestByWindow().get(issue.window);
    const resolved = later !== undefined
      && new Date(later.decidedAt).getTime() > new Date(issue.occurredAt).getTime()
      && later.reasonParams['forecastUnavailable'] !== 1;
    return resolved ? null : issue;
  });

  /**
   * Two decisions a day - so a last decision older than a day and a bit means the scheduled
   * jobs aren't running (or keep failing), and the inverter is on an old plan.
   */
  readonly stale = computed(() => {
    const decision = this.status()?.lastDecision;
    return decision !== null && decision !== undefined
      && this.now().getTime() - new Date(decision.decidedAt).getTime() > STALE_AFTER_MS;
  });

  /** Last 7 days of decisions, oldest first - the dial and legend need the latest one per window. */
  readonly recentDecisions = signal<ChargeDecisionDto[]>([]);

  private readonly latestByWindow = computed(() => {
    const latest = new Map<ChargeWindow, ChargeDecisionDto>();
    for (const decision of this.recentDecisions()) latest.set(decision.window, decision);
    return latest;
  });

  /** Each window's grid-charge target as a notch on the battery ring - only where it holds a reserve. */
  readonly reserves = computed<ReserveMark[]>(() =>
    [...this.latestByWindow().values()]
      .filter((d) => d.chargeFromGrid)
      .map((d) => ({ tone: d.window === 'OVERNIGHT' ? 'night' : 'day', soc: d.targetSocPercent }))
  );

  decisionFor(tone: WindowTone): ChargeDecisionDto | undefined {
    return this.latestByWindow().get(tone === 'night' ? 'OVERNIGHT' : 'AFTERNOON');
  }

  readonly windows = computed(() => parseTariffWindows(this.settings()?.planning.cheapTariffWindows ?? []));

  readonly nowIcon = computed(() => {
    const hour = this.now().getHours();
    return hour >= 6 && hour < 18 ? 'light_mode' : 'dark_mode';
  });

  readonly decisionMarks = computed<DecisionMark[]>(() => {
    const schedule = this.settings()?.schedule;
    if (!schedule) return [];
    const marks: [string | undefined, string, string][] = [
      [schedule.overnightTime, 'bolt', 'dashboard.dialDecision.OVERNIGHT'],
      [schedule.overnightRecheckTime, 'update', 'dashboard.dialDecision.RECHECK'],
      [schedule.afternoonTime, 'bolt', 'dashboard.dialDecision.AFTERNOON']
    ];
    return marks.flatMap(([time, icon, titleKey]) => {
      const hours = time ? hoursOf(time) : null;
      return hours === null ? [] : [{ hours, icon, titleKey, time: time! }];
    });
  });

  /** Both daily decisions, soonest first. */
  readonly upcoming = computed<UpcomingDecision[]>(() => {
    const schedule = this.settings()?.schedule;
    if (!schedule) return [];
    const now = this.now();
    const entries: [ChargeWindow, string, boolean][] = [
      ['OVERNIGHT', schedule.overnightTime, false],
      ['AFTERNOON', schedule.afternoonTime, false]
    ];
    if (schedule.overnightRecheckTime) entries.push(['OVERNIGHT', schedule.overnightRecheckTime, true]);
    return entries
      .flatMap(([window, time, recheck]) => {
        const hours = hoursOf(time);
        return hours === null ? [] : [{ window, recheck, ...nextOccurrence(hours, now) }];
      })
      .sort((a, b) => a.at.getTime() - b.at.getTime());
  });

  ngOnInit(): void {
    this.loadStatus();
    this.settingsApi.get().subscribe({
      next: (status) => this.settings.set(status.settings),
      error: () => this.settings.set(null)
    });
    // The "now" dot and "today/tomorrow" only need minute precision.
    this.clock = setInterval(() => this.now.set(new Date()), 60_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.clock);
  }

  loadStatus(): void {
    this.api.history().subscribe({
      next: (decisions) => this.recentDecisions.set(decisions),
      error: () => this.recentDecisions.set([])
    });
    this.api.status().subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
        this.errorKey.set(null);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorKey.set(settingsAwareErrorKey(error, 'dashboard.error'));
      }
    });
  }

  dayAndTime(iso: string): string {
    return formatDayAndTime(iso, this.language());
  }

  time(at: Date): string {
    return formatTime(at, this.language());
  }

  readonly period = decisionPeriod;
  readonly verdict = decisionVerdict;
  readonly reserve = decisionReserve;

  toneOf(window: ChargeWindow): string {
    return window === 'OVERNIGHT' ? 'tone-night' : 'tone-day';
  }

  iconOf(window: ChargeWindow): string {
    return window === 'OVERNIGHT' ? 'bedtime' : 'wb_sunny';
  }

  runOvernightNow(): void {
    this.runningOvernight.set(true);
    this.api.runOvernightNow().subscribe({
      next: () => {
        this.runningOvernight.set(false);
        this.loadStatus();
      },
      error: (error) => {
        this.runningOvernight.set(false);
        this.errorKey.set(settingsAwareErrorKey(error, 'dashboard.runError'));
      }
    });
  }

  runAfternoonNow(): void {
    this.runningAfternoon.set(true);
    this.api.runAfternoonNow().subscribe({
      next: () => {
        this.runningAfternoon.set(false);
        this.loadStatus();
      },
      error: (error) => {
        this.runningAfternoon.set(false);
        this.errorKey.set(settingsAwareErrorKey(error, 'dashboard.runError'));
      }
    });
  }
}
