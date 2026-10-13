export type WindowTone = 'night' | 'day';

/** A cheap tariff window ready to draw: hours past midnight plus the window's colour. */
export interface TariffWindowView {
  label: string;
  startHours: number;
  endHours: number;
  tone: WindowTone;
}

/** "21:45" -> 21.75; null when it isn't a valid HH:mm. */
export function hoursOf(time: string): number | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(time.trim());
  if (!match) return null;
  return Number(match[1]) + Number(match[2]) / 60;
}

/**
 * Parses the settings' "HH:mm-HH:mm" windows. A window that crosses midnight or starts in
 * the evening is the overnight one (night tone); anything else is the daytime top-up (day tone).
 */
export function parseTariffWindows(windows: string[]): TariffWindowView[] {
  return windows.flatMap((label) => {
    const [from, to] = label.split('-');
    const startHours = from !== undefined ? hoursOf(from) : null;
    const endHours = to !== undefined ? hoursOf(to) : null;
    if (startHours === null || endHours === null) return [];
    const tone: WindowTone = endHours <= startHours || startHours >= 18 ? 'night' : 'day';
    return [{ label: label.replace('-', '–'), startHours, endHours, tone }];
  });
}

/** The next moment the clock shows `hours`, from `now` - today or tomorrow. */
export function nextOccurrence(hours: number, now: Date): { at: Date; tomorrow: boolean } {
  const at = new Date(now);
  at.setHours(Math.floor(hours), Math.round((hours % 1) * 60), 0, 0);
  const tomorrow = at.getTime() <= now.getTime();
  if (tomorrow) at.setDate(at.getDate() + 1);
  return { at, tomorrow };
}
