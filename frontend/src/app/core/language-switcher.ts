import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AppLanguage, LanguageService } from './language.service';

let nextId = 0;

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="segmented" role="radiogroup" [attr.aria-label]="'language.label' | translate">
      @for (language of languages; track language) {
        <label>
          <input
            type="radio"
            [name]="groupName"
            [value]="language"
            [checked]="languageService.currentLanguage() === language"
            (change)="onChange(language)"
            [attr.aria-label]="'language.' + language | translate"
          />
          {{ language.toUpperCase() }}
        </label>
      }
    </div>
  `
})
export class LanguageSwitcher {
  readonly languages: AppLanguage[] = ['pl', 'en'];
  /** The shell renders two switchers (sidebar + phone top bar) - each needs its own radio group. */
  readonly groupName = `language-${nextId++}`;

  constructor(protected readonly languageService: LanguageService) {}

  onChange(language: AppLanguage): void {
    this.languageService.changeLanguage(language);
  }
}
