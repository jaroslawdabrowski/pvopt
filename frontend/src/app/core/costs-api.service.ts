import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DayCostDto {
  date: string;
  importCheapKwh: number;
  importExpensiveKwh: number;
  importCost: number;
  exportKwh: number;
  exportValue: number;
  loadKwh: number;
  baselineCost: number;
  netCost: number;
  savings: number;
  coveredHours: number;
  complete: boolean;
  /** The cheap/expensive split across a gap between snapshots was estimated. */
  approximate: boolean;
  exportPriceGaps: boolean;
}

export interface MonthlyCostsDto {
  month: string;
  pricing: { cheapRateGross: number; expensiveRateGross: number; fixedMonthlyGross: number; exportMultiplier: number };
  marketPricesUnavailable: boolean;
  days: DayCostDto[];
  totals: Omit<DayCostDto, 'date' | 'coveredHours' | 'complete' | 'approximate' | 'exportPriceGaps'> & { days: number; completeDays: number };
}

@Injectable({ providedIn: 'root' })
export class CostsApi {
  constructor(private readonly http: HttpClient) {}

  /** {@code month} as YYYY-MM. 409 {"error":"pricing.notConfigured"} until prices are entered in Settings. */
  monthly(month: string): Observable<MonthlyCostsDto> {
    return this.http.get<MonthlyCostsDto>('/api/costs', { params: { month } });
  }
}
