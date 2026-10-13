import { Component, OnInit, computed, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { EnergyPlanningApi, TouScheduleSlotDto } from '../core/energy-planning-api.service';
import { settingsAwareErrorKey } from '../core/settings-error.util';
import { hoursOf } from '../dashboard/tariff-windows';

/** One stretch of the 24h timeline bar, in percent of the day. */
interface TimelineSegment {
  slot: TouScheduleSlotDto;
  left: number;
  width: number;
}

@Component({
  selector: 'app-tou-schedule',
  standalone: true,
  imports: [DecimalPipe, RouterLink, TranslatePipe],
  templateUrl: './tou-schedule.html',
  styleUrl: './tou-schedule.scss'
})
export class TouSchedule implements OnInit {
  readonly slots = signal<TouScheduleSlotDto[]>([]);
  readonly loading = signal(true);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);

  /** Slots laid out on a 00-24 bar; a slot that wraps past midnight becomes two segments. */
  readonly segments = computed<TimelineSegment[]>(() =>
    this.slots().flatMap((slot) => {
      const start = hoursOf(slot.startTime);
      const end = hoursOf(slot.endTime);
      if (start === null || end === null) return [];
      const pct = (h: number) => (h / 24) * 100;
      if (end > start) return [{ slot, left: pct(start), width: pct(end - start) }];
      return [
        { slot, left: pct(start), width: pct(24 - start) },
        { slot, left: 0, width: pct(end) }
      ].filter((s) => s.width > 0);
    })
  );

  constructor(private readonly api: EnergyPlanningApi) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorKey.set(null);
    this.api.touSchedule().subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorKey.set(settingsAwareErrorKey(error, 'touSchedule.error'));
      }
    });
  }

  toneOf(slot: TouScheduleSlotDto): string {
    if (slot.governedBy === 'OVERNIGHT') return 'tone-night';
    if (slot.governedBy === 'AFTERNOON') return 'tone-day';
    return 'tone-neutral';
  }

  iconOf(slot: TouScheduleSlotDto): string {
    if (slot.governedBy === 'OVERNIGHT') return 'bedtime';
    if (slot.governedBy === 'AFTERNOON') return 'wb_sunny';
    return 'lock_clock';
  }
}
