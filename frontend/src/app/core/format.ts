import { AppLanguage } from './language.service';

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
