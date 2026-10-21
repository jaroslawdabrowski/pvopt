import { Component, ElementRef, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { DayCostDto } from '../core/costs-api.service';

const PAD = { top: 12, right: 8, bottom: 26, left: 48 };
const HEIGHT = 240;
const MAX_BAR = 16;

interface Slot {
  day: number;
  x: number;
  data: DayCostDto | null;
}

/**
 * Daily net cost of a month as columns from a zero baseline (below it when exports paid for
 * more than was bought), with a short ink marker per day at what that day would have cost
 * with no PV and no battery - the gap between marker and column is the saving. One colour,
 * one series: the marker is a reference, not a second series. Days with partial data are
 * drawn faded and say so in the tooltip and the table below.
 */
@Component({
  selector: 'app-cost-chart',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <svg [attr.width]="width()" [attr.height]="height" role="img" [attr.aria-label]="'costs.chartLabel' | translate">
      @for (tick of ticks(); track tick.value) {
        <line class="grid" [class.zero]="tick.value === 0" [attr.x1]="pad.left" [attr.x2]="width() - pad.right"
              [attr.y1]="tick.y" [attr.y2]="tick.y" />
        <text class="axis-label" [attr.x]="pad.left - 8" [attr.y]="tick.y">{{ axisMoney()(tick.value) }}</text>
      }
      @for (slot of slots(); track slot.day) {
        @if (hovered() === slot.day) {
          <rect class="hover-band" [attr.x]="slot.x - slotWidth() / 2" [attr.y]="pad.top" [attr.width]="slotWidth()"
                [attr.height]="plotHeight()" rx="6" />
        }
        @if (slot.data; as d) {
          <path class="bar" [class.partial]="!d.complete && !isToday(d)" [attr.d]="barPath(slot.x, d.netCost)" />
          <line class="baseline-marker" [attr.x1]="slot.x - barWidth() / 2 - 3" [attr.x2]="slot.x + barWidth() / 2 + 3"
                [attr.y1]="y(d.baselineCost)" [attr.y2]="y(d.baselineCost)" />
        }
        @if (labelled(slot.day)) {
          <text class="axis-label day" [attr.x]="slot.x" [attr.y]="height - 8">{{ slot.day }}</text>
        }
        <rect class="hit" [attr.x]="slot.x - slotWidth() / 2" [attr.y]="pad.top" [attr.width]="slotWidth()"
              [attr.height]="plotHeight()" (mouseenter)="hovered.set(slot.day)" (mouseleave)="hovered.set(null)"
              (click)="hovered.set(slot.day)" />
      }
    </svg>

    @if (hoveredSlot(); as s) {
      <div class="tooltip" [style.left.px]="tooltipLeft(s.x)">
        <p class="tooltip-title">{{ dayLabel()(s.day) }}</p>
        @if (s.data; as d) {
          <dl>
            <dt>{{ 'costs.tooltip.net' | translate }}</dt><dd class="num">{{ money()(d.netCost) }}</dd>
            <dt>{{ 'costs.tooltip.baseline' | translate }}</dt><dd class="num">{{ money()(d.baselineCost) }}</dd>
            <dt>{{ 'costs.tooltip.bought' | translate }}</dt><dd class="num">{{ kwh()(d.importCheapKwh + d.importExpensiveKwh) }}</dd>
            <dt>{{ 'costs.tooltip.sold' | translate }}</dt><dd class="num">{{ kwh()(d.exportKwh) }}</dd>
          </dl>
          @if (isToday(d)) {
            <p class="tooltip-note">{{ 'costs.todaySoFar' | translate }}</p>
          } @else if (!d.complete) {
            <p class="tooltip-note">{{ 'costs.partialDay' | translate: { hours: d.coveredHours } }}</p>
          }
        } @else {
          <p class="tooltip-note">{{ 'costs.noData' | translate }}</p>
        }
      </div>
    }
  `,
  styleUrl: './cost-chart.scss'
})
export class CostChart implements OnDestroy {
  readonly days = input.required<DayCostDto[]>();
  /** YYYY-MM */
  readonly month = input.required<string>();
  readonly money = input.required<(value: number) => string>();
  readonly kwh = input.required<(value: number) => string>();
  readonly dayLabel = input.required<(day: number) => string>();
  /** Whole złoty for the axis - grosze there are noise. */
  readonly axisMoney = input.required<(value: number) => string>();

  protected readonly pad = PAD;
  protected readonly height = HEIGHT;
  protected readonly hovered = signal<number | null>(null);
  protected readonly width = signal(640);

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly resize = new ResizeObserver((entries) => this.width.set(Math.max(280, entries[0].contentRect.width)));

  constructor() {
    this.resize.observe(this.host.nativeElement);
  }

  ngOnDestroy(): void {
    this.resize.disconnect();
  }

  protected readonly daysInMonth = computed(() => {
    const [year, month] = this.month().split('-').map(Number);
    return new Date(year, month, 0).getDate();
  });

  protected readonly plotHeight = computed(() => HEIGHT - PAD.top - PAD.bottom);
  protected readonly slotWidth = computed(() => (this.width() - PAD.left - PAD.right) / this.daysInMonth());
  protected readonly barWidth = computed(() => Math.min(MAX_BAR, Math.max(4, this.slotWidth() * 0.6)));

  protected readonly slots = computed<Slot[]>(() => {
    const byDay = new Map(this.days().map((d) => [Number(d.date.slice(8, 10)), d]));
    return Array.from({ length: this.daysInMonth() }, (_, i) => ({
      day: i + 1,
      x: PAD.left + this.slotWidth() * (i + 0.5),
      data: byDay.get(i + 1) ?? null
    }));
  });

  /** Nice round ticks spanning zero and every value (net cost and the baseline markers). */
  private readonly scale = computed(() => {
    const values = this.days().flatMap((d) => [d.netCost, d.baselineCost]);
    const rawMin = Math.min(0, ...values);
    const rawMax = Math.max(1, ...values);
    const step = niceStep((rawMax - rawMin) / 4);
    return { min: Math.floor(rawMin / step) * step, max: Math.ceil(rawMax / step) * step, step };
  });

  protected readonly ticks = computed(() => {
    const { min, max, step } = this.scale();
    const ticks = [];
    for (let v = min; v <= max + step / 2; v += step) {
      const value = Math.round(v * 100) / 100;
      ticks.push({ value, y: this.y(value) });
    }
    return ticks;
  });

  protected readonly hoveredSlot = computed(() => this.slots().find((s) => s.day === this.hovered()) ?? null);

  y(value: number): number {
    const { min, max } = this.scale();
    return PAD.top + ((max - value) / (max - min)) * this.plotHeight();
  }

  /** A column from the zero line, rounded 4px at its data end only. */
  barPath(x: number, value: number): string {
    const w = this.barWidth();
    const left = x - w / 2;
    const zero = this.y(0);
    const end = this.y(value);
    const length = Math.abs(zero - end);
    if (length < 0.5) return '';
    const r = Math.min(4, length, w / 2);
    if (value >= 0) {
      return `M${left},${zero} V${end + r} Q${left},${end} ${left + r},${end} H${left + w - r} Q${left + w},${end} ${left + w},${end + r} V${zero} Z`;
    }
    return `M${left},${zero} V${end - r} Q${left},${end} ${left + r},${end} H${left + w - r} Q${left + w},${end} ${left + w},${end - r} V${zero} Z`;
  }

  labelled(day: number): boolean {
    return day === 1 || day % 5 === 0 && day <= this.daysInMonth() - 2 || day === this.daysInMonth();
  }

  /** Beside the hovered day, never over it - to the right in the left half, to the left otherwise. */
  tooltipLeft(x: number): number {
    const gap = this.slotWidth() / 2 + 8;
    return x < this.width() / 2 ? x + gap : Math.max(0, x - gap - 236);
  }

  isToday(d: DayCostDto): boolean {
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return d.date === today;
  }
}

function niceStep(raw: number): number {
  const power = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 0.01))));
  const fraction = raw / power;
  return (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10) * power;
}
