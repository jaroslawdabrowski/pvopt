import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

export type AppLanguage = 'en' | 'pl';

interface LanguagePreferenceDto {
  language: string;
}

@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly currentLanguage = signal<AppLanguage>('en');

  constructor(
    private readonly http: HttpClient,
    private readonly translate: TranslateService
  ) {}

  /** Loads the persisted language preference from the backend and activates it. */
  async init(): Promise<void> {
    try {
      const preference = await firstValueFrom(this.http.get<LanguagePreferenceDto>('/api/preferences/language'));
      const language = preference.language.toLowerCase() as AppLanguage;
      this.currentLanguage.set(language);
      await firstValueFrom(this.translate.use(language));
    } catch {
      // keep the default (en) if the preference can't be loaded yet
    }
  }

  /** Switches language immediately in the UI and persists the choice. */
  changeLanguage(language: AppLanguage): void {
    this.currentLanguage.set(language);
    this.translate.use(language);
    this.http.put<LanguagePreferenceDto>('/api/preferences/language', { language: language.toUpperCase() }).subscribe();
  }
}
