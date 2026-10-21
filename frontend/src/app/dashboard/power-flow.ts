import { Component, computed, inject, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LivePowerDto } from '../core/energy-planning-api.service';
import { DirectionPanelDto, ProcessSettingsDto } from '../core/process-settings-api.service';
import { LanguageService } from '../core/language.service';
import { formatPower } from '../core/format';

/** Below this a flow reads as idle: the inverter's own noise, not energy moving. */
const IDLE_WATTS = 20;

const VIEW = { width: 400, height: 300 };
const PV = { x: 200, y: 118 };
const HOUSE = { x: 200, y: 222 };
const BATTERY = { x: 54, y: 222 };
const GRID = { x: 346, y: 222 };
const INPUT_Y = 34;

type Direction = 'north' | 'east' | 'south' | 'west';

interface Point {
  x: number;
  y: number;
}

interface Flow {
  id: string;
  /** Drawn from where the energy comes from to where it goes, so the dashes run with it. */
  path: string;
  tone: 'pv' | 'battery' | 'grid-cheap' | 'grid';
  active: boolean;
  /** Seconds per dash cycle - more power, faster dashes. */
  speed: number;
}

interface InputNode extends Point {
  /** Translation key of the direction, or null for an input no plane is mapped to. */
  labelKey: string | null;
  fallback: string;
  watts: number;
}

function line(from: Point, to: Point): string {
  return `M ${from.x} ${from.y} L ${to.x} ${to.y}`;
}

function speedFor(watts: number): number {
  // 0.3 kW crawls, 5 kW races: 2.4 s down to 0.5 s per dash cycle.
  const kw = Math.min(Math.abs(watts), 5000) / 1000;
  return Math.max(0.5, 2.4 - kw * 0.4);
}

/**
 * The house's power flows right now: each PV input (east, west) feeding one PV point that sums
 * them, and PV, battery and grid each joined to the house. Dashes run in the direction energy
 * moves; every value is also written out with a verb ("kupuje", "ładuje"), so neither colour nor
 * motion is the only signal. Nodes are HTML over the SVG, like the day dial's chips.
 */
@Component({
  selector: 'app-power-flow',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="flow" [class.stale]="stale()">
      <svg [attr.viewBox]="'0 0 ' + view.width + ' ' + view.height" aria-hidden="true">
        @for (flow of flows(); track flow.id) {
          <path class="track" [attr.d]="flow.path" />
          <path class="dash" [class]="'tone-' + flow.tone" [class.active]="flow.active" [attr.d]="flow.path"
                [style.animation-duration.s]="flow.speed" />
        }
      </svg>

      @for (node of inputs(); track $index) {
        <div class="node small tone-pv" [class.idle]="node.watts < idle" [style.left.%]="pct(node.x, 'x')" [style.top.%]="pct(node.y, 'y')">
          <span class="chip"><span class="material-symbols-rounded" aria-hidden="true">solar_power</span></span>
          <span class="label">
            <span class="value num">{{ power(node.watts) }}</span>
            <span class="sub">{{ node.labelKey ? (node.labelKey | translate) : node.fallback }}</span>
          </span>
        </div>
      }

      <div class="node tone-pv" [class.idle]="live().pvWatts < idle" [style.left.%]="pct(pv.x, 'x')" [style.top.%]="pct(pv.y, 'y')" data-testid="flow-pv">
        <span class="chip"><span class="material-symbols-rounded" aria-hidden="true">wb_sunny</span></span>
        <span class="label side">
          <span class="value num">{{ power(live().pvWatts) }}</span>
          <span class="sub">{{ 'dashboard.flow.pv' | translate }}</span>
        </span>
      </div>

      <div class="node tone-battery" [style.left.%]="pct(battery.x, 'x')" [style.top.%]="pct(battery.y, 'y')" data-testid="flow-battery">
        <span class="chip"><span class="material-symbols-rounded" aria-hidden="true">{{ batteryIcon() }}</span></span>
        <span class="label">
          <span class="value num">{{ live().batterySocPercent }}%</span>
          <span class="sub">{{ ('dashboard.flow.battery.' + batteryState()) | translate: { power: power(live().batteryWatts) } }}</span>
        </span>
      </div>

      <div class="node tone-house" [style.left.%]="pct(house.x, 'x')" [style.top.%]="pct(house.y, 'y')" data-testid="flow-house">
        <span class="chip"><span class="material-symbols-rounded" aria-hidden="true">home</span></span>
        <span class="label">
          <span class="value num">{{ power(live().loadWatts) }}</span>
          <span class="sub">{{ 'dashboard.flow.house' | translate }}</span>
        </span>
      </div>

      <div class="node" [class]="cheapNow() ? 'tone-grid-cheap' : 'tone-grid'" [style.left.%]="pct(grid.x, 'x')" [style.top.%]="pct(grid.y, 'y')" data-testid="flow-grid">
        <span class="chip"><span class="material-symbols-rounded" aria-hidden="true">electrical_services</span></span>
        <span class="label">
          <span class="value num">{{ gridState() === 'idle' ? '0 W' : power(live().gridWatts) }}</span>
          <span class="sub">{{ ('dashboard.flow.grid.' + gridState() + (cheapNow() && gridState() === 'buying' ? 'Cheap' : '')) | translate }}</span>
        </span>
      </div>
    </div>
  `,
  styleUrl: './power-flow.scss'
})
export class PowerFlow {
  readonly live = input.required<LivePowerDto>();
  readonly settings = input<ProcessSettingsDto | null>(null);
  readonly cheapNow = input(false);
  /** The last poll failed: keep showing the last reading, faded. */
  readonly stale = input(false);

  private readonly language = inject(LanguageService).currentLanguage;

  readonly view = VIEW;
  readonly pv = PV;
  readonly house = HOUSE;
  readonly battery = BATTERY;
  readonly grid = GRID;
  readonly idle = IDLE_WATTS;

  /** One node per inverter PV input, named after the plane(s) wired to it in the settings. */
  readonly inputs = computed<InputNode[]>(() => {
    const watts = this.live().pvInputWatts;
    const forecast = this.settings()?.forecast;
    const count = watts.length;
    return watts.map((w, index) => {
      const planes = forecast
        ? (['north', 'east', 'south', 'west'] as Direction[]).filter((d) => {
            const panel: DirectionPanelDto = forecast[d];
            return panel.kwp > 0 && panel.inverterInput === index + 1;
          })
        : [];
      const direction = planes.length === 1 ? planes[0] : null;
      // Spread evenly across the top, but east on the right and west on the left, as on a map.
      const spread = count === 1 ? PV.x : 100 + (200 * index) / (count - 1);
      const x = direction === 'east' ? 300 : direction === 'west' ? 100 : spread;
      return {
        x,
        y: INPUT_Y,
        labelKey: direction ? `dashboard.flow.direction.${direction}` : null,
        fallback: `PV${index + 1}`,
        watts: w
      };
    });
  });

  readonly batteryState = computed<'charging' | 'discharging' | 'idle'>(() => {
    const w = this.live().batteryWatts;
    if (Math.abs(w) < IDLE_WATTS) return 'idle';
    return w > 0 ? 'discharging' : 'charging';
  });

  readonly gridState = computed<'buying' | 'selling' | 'idle'>(() => {
    const w = this.live().gridWatts;
    if (Math.abs(w) < IDLE_WATTS) return 'idle';
    return w > 0 ? 'buying' : 'selling';
  });

  readonly batteryIcon = computed(() => {
    if (this.batteryState() === 'charging') return 'battery_charging_full';
    const soc = this.live().batterySocPercent;
    const bars = Math.min(6, Math.max(0, Math.round((soc / 100) * 6)));
    return soc >= 95 ? 'battery_full' : `battery_${bars}_bar`;
  });

  readonly flows = computed<Flow[]>(() => {
    const live = this.live();
    const flows: Flow[] = this.inputs().map((node, index) => ({
      id: `input-${index}`,
      path: line(node, PV),
      tone: 'pv',
      active: node.watts >= IDLE_WATTS,
      speed: speedFor(node.watts)
    }));
    flows.push({
      id: 'pv',
      path: line(PV, HOUSE),
      tone: 'pv',
      active: live.pvWatts >= IDLE_WATTS,
      speed: speedFor(live.pvWatts)
    });
    const battery = this.batteryState();
    flows.push({
      id: 'battery',
      path: battery === 'charging' ? line(HOUSE, BATTERY) : line(BATTERY, HOUSE),
      tone: 'battery',
      active: battery !== 'idle',
      speed: speedFor(live.batteryWatts)
    });
    const grid = this.gridState();
    flows.push({
      id: 'grid',
      path: grid === 'selling' ? line(HOUSE, GRID) : line(GRID, HOUSE),
      tone: this.cheapNow() ? 'grid-cheap' : 'grid',
      active: grid !== 'idle',
      speed: speedFor(live.gridWatts)
    });
    return flows;
  });

  pct(value: number, axis: 'x' | 'y'): number {
    return (value / (axis === 'x' ? VIEW.width : VIEW.height)) * 100;
  }

  power(watts: number): string {
    return formatPower(watts, this.language());
  }
}
