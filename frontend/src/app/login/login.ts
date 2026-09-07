import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { EnergyPlanningApi } from '../core/energy-planning-api.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {
  username = '';
  password = '';
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);
  readonly loading = signal(false);

  constructor(
    private readonly authService: AuthService,
    private readonly api: EnergyPlanningApi,
    private readonly router: Router
  ) {}

  submit(): void {
    this.errorKey.set(null);
    this.loading.set(true);
    this.authService.login(this.username, this.password);

    this.api.status().subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.loading.set(false);
        this.authService.logout();
        this.errorKey.set('login.error');
      }
    });
  }
}
