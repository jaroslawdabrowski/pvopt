import { Component, effect } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth.service';
import { LanguageService } from './core/language.service';
import { LanguageSwitcher } from './core/language-switcher';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  constructor(
    protected readonly authService: AuthService,
    private readonly languageService: LanguageService,
    private readonly router: Router
  ) {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        void this.languageService.init();
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
