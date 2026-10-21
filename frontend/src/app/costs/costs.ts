import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CostsApi, DayCostDto, MonthlyCostsDto } from '../core/costs-api.service';
import { LanguageService } from '../core/language.service';
import { settingsAwareErrorKey } from '../core/settings-error.util';
import { CostChart } from './cost-chart';

const LOCALES = { pl: 'pl-PL', en: 'en-GB' } as const;

@Component({
  selector: 'app-costs',
  standalone: true,
  imports: [RouterLink, TranslatePipe, CostChart],
  templateUrl: './costs.html',
  styleUrl: './costs.scss'
})
export class Costs implements OnInit {
  private readonly api = inject(CostsApi);
  private readonly language = inject(LanguageService).currentLanguage;

  readonly month = signal(currentMonth());
  readonly data = signal<MonthlyCostsDto | null>(null);
  readonly loading = signal(true);
  /** A translation key; 'costs.pricingNotConfigured' links to Settings. */
  readonly errorKey = signal<string | null>(null);

  readonly isCurrentMonth = computed(() => this.month() === currentMonth());

  readonly monthLabel = computed(() => {
    const [y, m] = this.month().split('-').map(Number);
    return new Intl.DateTimeFormat(LOCALES[this.language()], { month: 'long', year: 'numeric' })
      .format(new Date(y, m - 1, 1));
  });

  /** Average credit per exported kWh - the invoice shows the same figure ("RCE" column x 1.23). */
  readonly averageExportPrice = computed(() => {
    const t = this.data()?.totals;
    return t && t.exportKwh > 0 ? t.exportValue / t.exportKwh : null;
  });

  readonly sortedDays = computed(() => [...(this.data()?.days ?? [])].reverse());

  // Bound once so the chart's inputs stay stable between change detections.
  readonly moneyFn = (value: number) => this.money(value);
  readonly kwhFn = (value: number) => this.kwh(value);
  readonly axisMoneyFn = (value: number) =>
    new Intl.NumberFormat(LOCALES[this.language()], { style: 'currency', currency: 'PLN', maximumFractionDigits: 0 }).format(value);
  readonly dayLabelFn = (day: number) => {
    const [y, m] = this.month().split('-').map(Number);
    return new Intl.DateTimeFormat(LOCALES[this.language()], { weekday: 'long', day: 'numeric', month: 'long' })
      .format(new Date(y, m - 1, day));
  };

  ngOnInit(): void {
    this.load();
  }

  shift(months: number): void {
    const [y, m] = this.month().split('-').map(Number);
    const d = new Date(y, m - 1 + months, 1);
    this.month.set(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorKey.set(null);
    this.api.monthly(this.month()).subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.data.set(null);
        this.errorKey.set(error.status === 409 && error.error?.error === 'pricing.notConfigured'
          ? 'costs.pricingNotConfigured'
          : settingsAwareErrorKey(error, 'costs.error'));
      }
    });
  }

  /**
   * How much of what was bought from the grid was bought cheap - the number that says the
   * planning works: the battery should carry the expensive hours, so grid energy lands in
   * the cheap windows. Null when nothing was bought.
   */
  cheapShare(d: { importCheapKwh: number; importExpensiveKwh: number }): { cheap: number; expensive: number } | null {
    const total = d.importCheapKwh + d.importExpensiveKwh;
    return total > 0 ? { cheap: d.importCheapKwh / total, expensive: d.importExpensiveKwh / total } : null;
  }

  percent(share: number): string {
    return new Intl.NumberFormat(LOCALES[this.language()], { style: 'percent', maximumFractionDigits: 0 }).format(share);
  }

  money(value: number): string {
    return new Intl.NumberFormat(LOCALES[this.language()], { style: 'currency', currency: 'PLN' }).format(value);
  }

  kwh(value: number): string {
    return new Intl.NumberFormat(LOCALES[this.language()], { maximumFractionDigits: 1, minimumFractionDigits: 1 })
      .format(value) + ' kWh';
  }

  rate(value: number): string {
    return new Intl.NumberFormat(LOCALES[this.language()], { minimumFractionDigits: 2, maximumFractionDigits: 3 })
      .format(value) + ' zł/kWh';
  }

  isToday(day: DayCostDto): boolean {
    const now = new Date();
    return day.date === `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }

  shortDay(day: DayCostDto): string {
    return new Intl.DateTimeFormat(LOCALES[this.language()], { weekday: 'short', day: 'numeric' }).format(new Date(day.date));
  }
}

function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
