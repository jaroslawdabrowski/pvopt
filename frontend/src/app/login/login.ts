import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/auth.service';
import { EnergyPlanningApi } from '../core/energy-planning-api.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, TranslatePipe, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
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
      error: (error) => {
        this.loading.set(false);
        if (error.status === 401) {
          this.authService.logout();
          this.errorKey.set('login.error');
        } else {
          // Credentials were accepted (a non-401 error, e.g. 409 settings.notConfigured,
          // means the request reached the app) - let the user in, the dashboard shows
          // whatever the actual problem is.
          this.router.navigateByUrl('/dashboard');
        }
      }
    });
  }
}
