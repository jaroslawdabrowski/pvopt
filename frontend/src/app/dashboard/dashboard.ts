import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { EnergyPlanningApi, EnergyStatusDto } from '../core/energy-planning-api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, TranslatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {
  readonly status = signal<EnergyStatusDto | null>(null);
  readonly loading = signal(true);
  readonly runningOvernight = signal(false);
  readonly runningAfternoon = signal(false);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);

  constructor(private readonly api: EnergyPlanningApi) {}

  ngOnInit(): void {
    this.loadStatus();
  }

  loadStatus(): void {
    this.loading.set(true);
    this.api.status().subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
        this.errorKey.set(null);
      },
      error: () => {
        this.loading.set(false);
        this.errorKey.set('dashboard.error');
      }
    });
  }

  runOvernightNow(): void {
    this.runningOvernight.set(true);
    this.api.runOvernightNow().subscribe({
      next: () => {
        this.runningOvernight.set(false);
        this.loadStatus();
      },
      error: () => {
        this.runningOvernight.set(false);
        this.errorKey.set('dashboard.runError');
      }
    });
  }

  runAfternoonNow(): void {
    this.runningAfternoon.set(true);
    this.api.runAfternoonNow().subscribe({
      next: () => {
        this.runningAfternoon.set(false);
        this.loadStatus();
      },
      error: () => {
        this.runningAfternoon.set(false);
        this.errorKey.set('dashboard.runError');
      }
    });
  }
}
