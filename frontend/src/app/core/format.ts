import { AppLanguage } from './language.service';
import { ChargeDecisionDto } from './energy-planning-api.service';

const LOCALES: Record<AppLanguage, string> = { pl: 'pl-PL', en: 'en-GB' };

/** "13 października, 21:45" / "13 October, 21:45" - in the UI language, via Intl (no Angular locale data). */
export function formatDayAndTime(iso: string | Date, language: AppLanguage): string {
  return new Intl.DateTimeFormat(LOCALES[language], {
    day: 'numeric',
    month: 'long',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(iso));
}

export function formatTime(iso: string | Date, language: AppLanguage): string {
  return new Intl.DateTimeFormat(LOCALES[language], { hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}

/** "poniedziałek, 12 października" - a day header for grouped lists. */
export function formatDayHeader(iso: string | Date, language: AppLanguage): string {
  return new Intl.DateTimeFormat(LOCALES[language], { weekday: 'long', day: 'numeric', month: 'long' }).format(
    new Date(iso)
  );
}

/** Local calendar day key, for grouping. */
export function dayKey(iso: string | Date): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

/** "6" / "6:30" - an hour of day as people say it. */
export function formatHour(hours: number): string {
  const whole = Math.floor(hours);
  const minutes = Math.round((hours - whole) * 60);
  return minutes === 0 ? String(whole) : `${whole}:${String(minutes).padStart(2, '0')}`;
}

/** The expensive period a decision covered, from its reason params - absent on decisions made before it was recorded. */
export function decisionPeriod(params: Record<string, number>): { from: string; to: string } | null {
  const from = params['periodFromHour'];
  const to = params['periodToHour'];
  return from === undefined || to === undefined ? null : { from: formatHour(from), to: formatHour(to) };
}

/** Which verdict a decision reads as: charging, holding a reserve without charging, or neither. */
export function decisionVerdict(d: ChargeDecisionDto): 'chargeOn' | 'reserveHeld' | 'chargeOff' {
  if (d.chargeFromGrid) return 'chargeOn';
  return d.reasonCode === 'RESERVE_HELD' ? 'reserveHeld' : 'chargeOff';
}

/** "4,9" / "4.9" - kWh to one decimal in the UI language. */
export function formatKwh(kwh: number, language: AppLanguage): string {
  return new Intl.NumberFormat(LOCALES[language], { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(kwh);
}

/** The period's largest running deficit and when it peaks - absent on decisions made before it was computed. */
export function decisionReserve(params: Record<string, number>): { kwh: number; hour: string; learnedShape: boolean } | null {
  const kwh = params['reserveKwh'];
  const peak = params['reservePeakHour'];
  return kwh === undefined || peak === undefined
    ? null
    : { kwh, hour: formatClock(peak), learnedShape: params['hourlyShape'] === 1 };
}

/** "9:00" / "9:30" - an hour of day as a clock time. */
export function formatClock(hours: number): string {
  const whole = Math.floor(hours) % 24;
  const minutes = Math.round((hours - Math.floor(hours)) * 60);
  return `${whole}:${String(minutes).padStart(2, '0')}`;
}

/** "22" / "6:30" - a boundary time as a short label: whole hours without minutes. */
export function shortClock(hours: number): string {
  const whole = Math.floor(hours) % 24;
  const minutes = Math.round((hours - Math.floor(hours)) * 60);
  return minutes === 0 ? String(whole).padStart(2, '0') : `${whole}:${String(minutes).padStart(2, '0')}`;
}

/** "15 października" / "15 October" - a day without the time. */
export function formatDay(iso: string | Date, language: AppLanguage): string {
  return new Intl.DateTimeFormat(LOCALES[language], { day: 'numeric', month: 'long' }).format(new Date(iso));
}
