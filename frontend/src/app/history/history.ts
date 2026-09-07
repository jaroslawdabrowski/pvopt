import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { ChargeDecisionDto, EnergyPlanningApi } from '../core/energy-planning-api.service';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [DatePipe, TranslatePipe],
  templateUrl: './history.html',
  styleUrl: './history.scss'
})
export class History implements OnInit {
  readonly decisions = signal<ChargeDecisionDto[]>([]);
  readonly loading = signal(true);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);

  constructor(private readonly api: EnergyPlanningApi) {}

  ngOnInit(): void {
    this.api.history().subscribe({
      next: (decisions) => {
        this.decisions.set([...decisions].reverse());
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorKey.set('history.error');
      }
    });
  }
}
