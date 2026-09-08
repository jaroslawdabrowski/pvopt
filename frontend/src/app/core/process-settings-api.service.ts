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
    dailyConsumptionEstimateKwh: number;
    afternoonConsumptionEstimateKwh: number;
    forecastSafetyMarginRatio: number;
    cheapTariffWindows: string[];
  };
  schedule: {
    overnightTime: string;
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
      dailyConsumptionEstimateKwh: 0,
      afternoonConsumptionEstimateKwh: 0,
      forecastSafetyMarginRatio: 1.2,
      cheapTariffWindows: ['22:00-06:00', '13:00-15:00']
    },
    schedule: {
      overnightTime: '21:45',
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
    }
  };
}

@Injectable({ providedIn: 'root' })
export class ProcessSettingsApi {
  constructor(private readonly http: HttpClient) {}

  get(): Observable<ProcessSettingsStatusDto> {
    return this.http.get<ProcessSettingsStatusDto>('/api/settings');
  }

  save(settings: ProcessSettingsDto): Observable<ProcessSettingsDto> {
    return this.http.put<ProcessSettingsDto>('/api/settings', settings);
  }
}
