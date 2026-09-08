import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  ProcessSettingsApi,
  ProcessSettingsDto,
  blankProcessSettings
} from '../core/process-settings-api.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class Settings implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProcessSettingsApi);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly configured = signal(false);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);
  readonly savedJustNow = signal(false);

  readonly form = this.fb.group({
    planning: this.fb.group({
      minSocPercent: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
      fullSocPercent: [100, [Validators.required, Validators.min(0), Validators.max(100)]],
      dailyConsumptionEstimateKwh: [0, [Validators.required, Validators.min(0)]],
      afternoonConsumptionEstimateKwh: [0, [Validators.required, Validators.min(0)]],
      forecastSafetyMarginRatio: [1.2, [Validators.required, Validators.min(0.01)]],
      cheapTariffWindows: ['', Validators.required]
    }),
    schedule: this.fb.group({
      overnightTime: ['21:45', Validators.required],
      afternoonTime: ['12:45', Validators.required]
    }),
    forecast: this.fb.group({
      latitude: [0, [Validators.required, Validators.min(-90), Validators.max(90)]],
      longitude: [0, [Validators.required, Validators.min(-180), Validators.max(180)]],
      north: this.directionGroup(),
      east: this.directionGroup(),
      south: this.directionGroup(),
      west: this.directionGroup()
    }),
    inverterConnection: this.fb.group({
      host: ['', Validators.required],
      port: [8899, [Validators.required, Validators.min(1), Validators.max(65535)]],
      loggerSerial: [0, [Validators.required, Validators.min(1)]]
    })
  });

  ngOnInit(): void {
    this.load();
  }

  private directionGroup() {
    return this.fb.group({
      declinationDegrees: [0, [Validators.required, Validators.min(0), Validators.max(90)]],
      kwp: [0, [Validators.required, Validators.min(0)]]
    });
  }

  load(): void {
    this.loading.set(true);
    this.errorKey.set(null);
    this.api.get().subscribe({
      next: (status) => {
        this.configured.set(status.configured);
        this.patchForm(status.settings ?? blankProcessSettings());
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorKey.set('settings.loadError');
      }
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.errorKey.set(null);
    this.savedJustNow.set(false);
    this.api.save(this.toDto()).subscribe({
      next: (settings) => {
        this.saving.set(false);
        this.configured.set(true);
        this.savedJustNow.set(true);
        this.patchForm(settings);
      },
      error: () => {
        this.saving.set(false);
        this.errorKey.set('settings.saveError');
      }
    });
  }

  private patchForm(settings: ProcessSettingsDto): void {
    this.form.patchValue({
      planning: {
        ...settings.planning,
        cheapTariffWindows: settings.planning.cheapTariffWindows.join(', ')
      },
      schedule: settings.schedule,
      forecast: settings.forecast,
      inverterConnection: settings.inverterConnection
    });
  }

  private toDto(): ProcessSettingsDto {
    const value = this.form.getRawValue();
    return {
      planning: {
        ...value.planning!,
        cheapTariffWindows: value.planning!.cheapTariffWindows!
          .split(',')
          .map((w) => w.trim())
          .filter((w) => w.length > 0)
      },
      schedule: value.schedule!,
      forecast: value.forecast!,
      inverterConnection: value.inverterConnection!
    } as ProcessSettingsDto;
  }
}
