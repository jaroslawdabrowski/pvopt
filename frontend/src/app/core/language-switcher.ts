import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AppLanguage, LanguageService } from './language.service';

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <select
      [value]="languageService.currentLanguage()"
      (change)="onChange($any($event.target).value)"
      [attr.aria-label]="'language.label' | translate"
    >
      <option value="en">{{ 'language.en' | translate }}</option>
      <option value="pl">{{ 'language.pl' | translate }}</option>
    </select>
  `
})
export class LanguageSwitcher {
  constructor(protected readonly languageService: LanguageService) {}

  onChange(language: AppLanguage): void {
    this.languageService.changeLanguage(language);
  }
}
