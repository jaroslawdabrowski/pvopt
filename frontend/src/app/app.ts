import { Component, effect } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth.service';
import { LanguageService } from './core/language.service';
import { LanguageSwitcher } from './core/language-switcher';

interface NavItem {
  path: string;
  icon: string;
  labelKey: string;
  /** Shorter label for the phone tab bar. */
  shortKey: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly navItems: NavItem[] = [
    { path: '/dashboard', icon: 'home', labelKey: 'nav.dashboard', shortKey: 'nav.dashboardShort' },
    { path: '/tou-schedule', icon: 'schedule', labelKey: 'nav.touSchedule', shortKey: 'nav.touScheduleShort' },
    { path: '/history', icon: 'history', labelKey: 'nav.history', shortKey: 'nav.historyShort' },
    { path: '/costs', icon: 'payments', labelKey: 'nav.costs', shortKey: 'nav.costsShort' },
    { path: '/settings', icon: 'tune', labelKey: 'nav.settings', shortKey: 'nav.settingsShort' }
  ];

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
