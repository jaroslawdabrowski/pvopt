import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ChargeWindow, EnergyPlanningApi, EnergyStatusDto } from '../core/energy-planning-api.service';
import { ProcessSettingsApi, ProcessSettingsDto } from '../core/process-settings-api.service';
import { settingsAwareErrorKey } from '../core/settings-error.util';
import { LanguageService } from '../core/language.service';
import { decisionPeriod, formatDayAndTime, formatTime } from '../core/format';
import { DayDial } from './day-dial';
import { hoursOf, nextOccurrence, parseTariffWindows } from './tariff-windows';

interface UpcomingDecision {
  window: ChargeWindow;
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

  readonly windows = computed(() => parseTariffWindows(this.settings()?.planning.cheapTariffWindows ?? []));

  readonly decisionHours = computed(() => {
    const schedule = this.settings()?.schedule;
    if (!schedule) return [];
    return [hoursOf(schedule.overnightTime), hoursOf(schedule.afternoonTime)].filter((h): h is number => h !== null);
  });

  /** Both daily decisions, soonest first. */
  readonly upcoming = computed<UpcomingDecision[]>(() => {
    const schedule = this.settings()?.schedule;
    if (!schedule) return [];
    const now = this.now();
    const entries: [ChargeWindow, string][] = [
      ['OVERNIGHT', schedule.overnightTime],
      ['AFTERNOON', schedule.afternoonTime]
    ];
    return entries
      .flatMap(([window, time]) => {
        const hours = hoursOf(time);
        return hours === null ? [] : [{ window, ...nextOccurrence(hours, now) }];
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
