import { Component, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EnergyPlanningApi, TouScheduleSlotDto } from '../core/energy-planning-api.service';

@Component({
  selector: 'app-tou-schedule',
  standalone: true,
  imports: [
    DecimalPipe,
    TranslatePipe,
    MatTableModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './tou-schedule.html',
  styleUrl: './tou-schedule.scss'
})
export class TouSchedule implements OnInit {
  readonly columns = ['startTime', 'endTime', 'powerWatts', 'targetSocPercent', 'gridChargeEnabled', 'governedBy'];
  readonly slots = signal<TouScheduleSlotDto[]>([]);
  readonly loading = signal(true);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);

  constructor(private readonly api: EnergyPlanningApi) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorKey.set(null);
    this.api.touSchedule().subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorKey.set('touSchedule.error');
      }
    });
  }
}
