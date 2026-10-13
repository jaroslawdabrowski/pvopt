import { Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { TariffWindowView, WindowTone } from './tariff-windows';

const CENTER = 120;
const DAY_RING_RADIUS = 104;
const SOC_RING_RADIUS = 72;
const HOUR_LABEL_RADIUS = 87;
const SOC_CIRCUMFERENCE = 2 * Math.PI * SOC_RING_RADIUS;

/** A point on a circle around the dial's centre, `hours` past midnight (midnight at the top). */
function pointAt(hours: number, radius: number): { x: number; y: number } {
  const angle = (hours / 24) * 2 * Math.PI;
  return { x: CENTER + radius * Math.sin(angle), y: CENTER - radius * Math.cos(angle) };
}

export interface ReserveMark {
  tone: WindowTone;
  soc: number;
}

function arcPath(startHours: number, endHours: number, radius: number): string {
  const span = (endHours - startHours + 24) % 24 || 24;
  const start = pointAt(startHours, radius);
  const end = pointAt(startHours + span, radius);
  const largeArc = span > 12 ? 1 : 0;
  return `M ${start.x} ${start.y} A ${radius} ${radius} 0 ${largeArc} 1 ${end.x} ${end.y}`;
}

/**
 * The dashboard's one memorable element: the day as a 24-hour ring, midnight at the top,
 * with the cheap G12 windows drawn in their window's colour, a dot for "now", ticks for the
 * two daily decision times, and the battery's state of charge filling the inner ring.
 */
@Component({
  selector: 'app-day-dial',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <svg viewBox="0 0 240 240" role="img" [attr.aria-label]="'dashboard.dialLabel' | translate: { soc: socPercent() }">
      <circle class="day-track" [attr.cx]="center" [attr.cy]="center" [attr.r]="dayRadius" />
      @for (window of arcs(); track window.label) {
        <path class="window-arc" [class]="'tone-' + window.tone" [attr.d]="window.path" />
      }
      @for (label of hourLabels; track label.text) {
        <text class="hour-label" [attr.x]="label.x" [attr.y]="label.y">{{ label.text }}</text>
      }
      @for (tick of decisionTicks(); track $index) {
        <line class="decision-tick" [attr.x1]="tick.x1" [attr.y1]="tick.y1" [attr.x2]="tick.x2" [attr.y2]="tick.y2" />
      }

      <circle class="soc-track" [attr.cx]="center" [attr.cy]="center" [attr.r]="socRadius" />
      <circle
        class="soc-fill"
        [attr.cx]="center"
        [attr.cy]="center"
        [attr.r]="socRadius"
        [style.stroke-dasharray]="socCircumference"
        [style.--soc-offset]="socOffset()"
        [style.--soc-full]="socCircumference"
        [attr.transform]="'rotate(-90 ' + center + ' ' + center + ')'"
      />

      <circle class="now-dot" [attr.cx]="nowPoint().x" [attr.cy]="nowPoint().y" r="7" />

      @for (badge of reserveBadges(); track badge.tone) {
        <g class="reserve-badge" [class]="'tone-' + badge.tone">
          <rect [attr.x]="badge.x - badgeWidth / 2" [attr.y]="badge.y - badgeHeight / 2" [attr.width]="badgeWidth"
                [attr.height]="badgeHeight" [attr.rx]="badgeHeight / 2" />
          <text [attr.x]="badge.x" [attr.y]="badge.y">{{ badge.soc }}%</text>
        </g>
      }
    </svg>

    <div class="dial-centre">
      <span class="soc num">{{ socPercent() }}<span class="unit">%</span></span>
      <span class="soc-label">{{ 'dashboard.batterySoc' | translate }}</span>
    </div>
  `,
  styleUrl: './day-dial.scss'
})
export class DayDial {
  readonly socPercent = input.required<number>();
  readonly windows = input<TariffWindowView[]>([]);
  /** Decision times as hours past midnight (e.g. 21.75 for 21:45). */
  readonly decisionHours = input<number[]>([]);
  readonly now = input.required<Date>();
  /** The SOC each cheap window holds (its grid-charge target) - drawn as a badge on that window's arc. */
  readonly reserves = input<ReserveMark[]>([]);

  protected readonly center = CENTER;
  protected readonly dayRadius = DAY_RING_RADIUS;
  protected readonly socRadius = SOC_RING_RADIUS;
  protected readonly socCircumference = SOC_CIRCUMFERENCE;

  protected readonly hourLabels = [0, 6, 12, 18].map((hour) => {
    const p = pointAt(hour, HOUR_LABEL_RADIUS);
    return { text: String(hour).padStart(2, '0'), x: p.x, y: p.y };
  });

  protected readonly arcs = computed(() =>
    this.windows().map((w) => ({
      label: w.label,
      tone: w.tone,
      path: arcPath(w.startHours, w.endHours, DAY_RING_RADIUS)
    }))
  );

  protected readonly decisionTicks = computed(() =>
    this.decisionHours().map((hours) => {
      const inner = pointAt(hours, DAY_RING_RADIUS - 12);
      const outer = pointAt(hours, DAY_RING_RADIUS + 12);
      return { x1: inner.x, y1: inner.y, x2: outer.x, y2: outer.y };
    })
  );

  protected readonly nowPoint = computed(() => {
    const now = this.now();
    return pointAt(now.getHours() + now.getMinutes() / 60, DAY_RING_RADIUS);
  });

  protected readonly badgeWidth = 38;
  protected readonly badgeHeight = 20;

  /**
   * A window's reserve sits on that window's arc, at its middle: the outer ring is time, so
   * "36%" on the night arc reads as "overnight the battery holds 36%". The inner ring stays a
   * plain charge level - putting reserves on it mixed two scales on one circle.
   */
  protected readonly reserveBadges = computed(() =>
    this.reserves().flatMap((reserve) => {
      const window = this.windows().find((w) => w.tone === reserve.tone);
      if (!window) return [];
      const span = (window.endHours - window.startHours + 24) % 24 || 24;
      const middle = pointAt(window.startHours + span / 2, DAY_RING_RADIUS);
      return [{ tone: reserve.tone, soc: reserve.soc, x: middle.x, y: middle.y }];
    })
  );

  protected readonly socOffset = computed(() => {
    const soc = Math.min(100, Math.max(0, this.socPercent()));
    return SOC_CIRCUMFERENCE * (1 - soc / 100);
  });
}
