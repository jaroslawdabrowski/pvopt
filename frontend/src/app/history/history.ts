import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ChargeDecisionDto, ChargeWindow, EnergyPlanningApi } from '../core/energy-planning-api.service';
import { LanguageService } from '../core/language.service';
import { dayKey, decisionPeriod, decisionVerdict, formatDayHeader, formatTime } from '../core/format';

interface DayGroup {
  key: string;
  /** 'today' / 'yesterday' get a translated header; other days a formatted date. */
  relative: 'today' | 'yesterday' | null;
  date: string;
  decisions: ChargeDecisionDto[];
}

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './history.html',
  styleUrl: './history.scss'
})
export class History implements OnInit {
  private readonly api = inject(EnergyPlanningApi);
  private readonly language = inject(LanguageService).currentLanguage;

  readonly decisions = signal<ChargeDecisionDto[]>([]);
  readonly loading = signal(true);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);

  /** Newest first, one group per calendar day. */
  readonly days = computed<DayGroup[]>(() => {
    const today = dayKey(new Date());
    const yesterdayDate = new Date();
    yesterdayDate.setDate(yesterdayDate.getDate() - 1);
    const yesterday = dayKey(yesterdayDate);

    const groups: DayGroup[] = [];
    for (const decision of this.decisions()) {
      const key = dayKey(decision.decidedAt);
      let group = groups.at(-1);
      if (!group || group.key !== key) {
        group = {
          key,
          relative: key === today ? 'today' : key === yesterday ? 'yesterday' : null,
          date: decision.decidedAt,
          decisions: []
        };
        groups.push(group);
      }
      group.decisions.push(decision);
    }
    return groups;
  });

  ngOnInit(): void {
    this.api.history().subscribe({
      next: (decisions) => {
        this.decisions.set([...decisions].reverse());
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorKey.set('history.error');
      }
    });
  }

  dayHeader(iso: string): string {
    return formatDayHeader(iso, this.language());
  }

  time(iso: string): string {
    return formatTime(iso, this.language());
  }

  readonly period = decisionPeriod;
  readonly verdict = decisionVerdict;

  toneOf(window: ChargeWindow): string {
    return window === 'OVERNIGHT' ? 'tone-night' : 'tone-day';
  }

  iconOf(window: ChargeWindow): string {
    return window === 'OVERNIGHT' ? 'bedtime' : 'wb_sunny';
  }
}
