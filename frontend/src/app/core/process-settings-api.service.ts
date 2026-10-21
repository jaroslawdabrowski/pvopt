import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DirectionPanelDto {
  declinationDegrees: number;
  kwp: number;
}

export interface ProcessSettingsDto {
  planning: {
    minSocPercent: number;
    fullSocPercent: number;
    batteryCapacityKwh: number;
    consumptionAfterOvernightKwh: number;
    consumptionAfterAfternoonKwh: number;
    forecastSafetyMarginRatio: number;
    cheapTariffWindows: string[];
  };
  schedule: {
    overnightTime: string;
    /** Second overnight decision inside the window; '' = off. */
    overnightRecheckTime: string;
    afternoonTime: string;
  };
  forecast: {
    latitude: number;
    longitude: number;
    north: DirectionPanelDto;
    east: DirectionPanelDto;
    south: DirectionPanelDto;
    west: DirectionPanelDto;
  };
  inverterConnection: {
    host: string;
    port: number;
    loggerSerial: number;
  };
  notifications: {
    /** A full ntfy topic URL; blank = notifications off. */
    ntfyUrl: string;
  };
  /** Gross zł/kWh per zone and zł/month fixed; null until entered (only the cost page needs it). */
  pricing: {
    cheapRateGross: number;
    expensiveRateGross: number;
    fixedMonthlyGross: number;
    exportMultiplier: number;
  } | null;
}

export interface ProcessSettingsStatusDto {
  configured: boolean;
  settings: ProcessSettingsDto | null;
}

export function blankProcessSettings(): ProcessSettingsDto {
  const blankDirection: DirectionPanelDto = { declinationDegrees: 0, kwp: 0 };
  return {
    planning: {
      minSocPercent: 20,
      fullSocPercent: 100,
      batteryCapacityKwh: 0,
      consumptionAfterOvernightKwh: 0,
      consumptionAfterAfternoonKwh: 0,
      forecastSafetyMarginRatio: 1.2,
      cheapTariffWindows: ['22:00-06:00', '13:00-15:00']
    },
    schedule: {
      overnightTime: '21:45',
      overnightRecheckTime: '03:30',
      afternoonTime: '12:45'
    },
    forecast: {
      latitude: 0,
      longitude: 0,
      north: { ...blankDirection },
      east: { ...blankDirection },
      south: { ...blankDirection },
      west: { ...blankDirection }
    },
    inverterConnection: {
      host: '',
      port: 8899,
      loggerSerial: 0
    },
    notifications: {
      ntfyUrl: ''
    },
    pricing: null
  };
}

@Injectable({ providedIn: 'root' })
export class ProcessSettingsApi {
  constructor(private readonly http: HttpClient) {}

  get(): Observable<ProcessSettingsStatusDto> {
    return this.http.get<ProcessSettingsStatusDto>('/api/settings');
  }

  testNotification(ntfyUrl: string): Observable<{ delivered: boolean }> {
    return this.http.post<{ delivered: boolean }>('/api/notifications/test', { ntfyUrl });
  }

  save(settings: ProcessSettingsDto): Observable<ProcessSettingsDto> {
    return this.http.put<ProcessSettingsDto>('/api/settings', settings);
  }
}
