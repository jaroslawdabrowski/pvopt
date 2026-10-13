import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import {
  ProcessSettingsApi,
  ProcessSettingsDto,
  blankProcessSettings
} from '../core/process-settings-api.service';
import { ChargeWindow, EnergyPlanningApi, LearnedConsumptionDto } from '../core/energy-planning-api.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class Settings implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProcessSettingsApi);
  private readonly energyApi = inject(EnergyPlanningApi);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly configured = signal(false);
  /** Holds a translation key (not a rendered message) so the template can localize it. */
  readonly errorKey = signal<string | null>(null);
  readonly savedJustNow = signal(false);
  readonly directions = ['north', 'east', 'south', 'west'] as const;
  /** Measured consumption per window - once there's enough of it, the planner uses it instead of the field. */
  readonly learned = signal<Record<ChargeWindow, LearnedConsumptionDto | null> | null>(null);

  readonly form = this.fb.group({
    planning: this.fb.group({
      minSocPercent: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
      fullSocPercent: [100, [Validators.required, Validators.min(0), Validators.max(100)]],
      batteryCapacityKwh: [0, [Validators.required, Validators.min(0.1)]],
      consumptionAfterOvernightKwh: [0, [Validators.required, Validators.min(0)]],
      consumptionAfterAfternoonKwh: [0, [Validators.required, Validators.min(0)]],
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
    }),
    notifications: this.fb.group({
      ntfyUrl: ['', Validators.pattern(/^\s*(https?:\/\/\S+)?\s*$/)]
    })
  });

  readonly testing = signal(false);
  /** Result of the last test notification: a translation key, or null before any test. */
  readonly testResultKey = signal<string | null>(null);

  sendTestNotification(): void {
    const url = this.form.controls.notifications.controls.ntfyUrl.value ?? '';
    this.testing.set(true);
    this.testResultKey.set(null);
    this.api.testNotification(url).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.testResultKey.set(result.delivered ? 'settings.notifications.testDelivered' : 'settings.notifications.testFailed');
      },
      error: () => {
        this.testing.set(false);
        this.testResultKey.set('settings.notifications.testFailed');
      }
    });
  }

  ngOnInit(): void {
    this.load();
    this.energyApi.learnedConsumption().subscribe({
      next: (learned) => this.learned.set(learned),
      error: () => this.learned.set(null)
    });
  }

  hasPanel(direction: (typeof this.directions)[number]): boolean {
    return Number(this.form.controls.forecast.controls[direction].controls.kwp.value) > 0;
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
      this.savedJustNow.set(false);
      this.errorKey.set('settings.invalid');
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
      inverterConnection: settings.inverterConnection,
      notifications: settings.notifications ?? { ntfyUrl: '' }
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
      inverterConnection: value.inverterConnection!,
      notifications: { ntfyUrl: (value.notifications?.ntfyUrl ?? '').trim() }
    } as ProcessSettingsDto;
  }
}
