import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type ChargeWindow = 'OVERNIGHT' | 'AFTERNOON';

export interface ChargeDecisionDto {
  decidedAt: string;
  window: ChargeWindow;
  chargeFromGrid: boolean;
  targetSocPercent: number;
  reasonCode: string;
  reasonParams: Record<string, number>;
}

export interface EnergyStatusDto {
  batterySocPercent: number;
  tariffRate: 'CHEAP' | 'EXPENSIVE';
  lastDecision: ChargeDecisionDto | null;
}

@Injectable({ providedIn: 'root' })
export class EnergyPlanningApi {
  constructor(private readonly http: HttpClient) {}

  status(): Observable<EnergyStatusDto> {
    return this.http.get<EnergyStatusDto>('/api/status');
  }

  history(from?: string, to?: string): Observable<ChargeDecisionDto[]> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return this.http.get<ChargeDecisionDto[]>('/api/history', { params });
  }

  runOvernightNow(): Observable<ChargeDecisionDto> {
    return this.http.post<ChargeDecisionDto>('/api/schedule/run-now/overnight', {});
  }

  runAfternoonNow(): Observable<ChargeDecisionDto> {
    return this.http.post<ChargeDecisionDto>('/api/schedule/run-now/afternoon', {});
  }
}
