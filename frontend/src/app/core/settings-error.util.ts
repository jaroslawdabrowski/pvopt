import { HttpErrorResponse } from '@angular/common/http';

/**
 * The backend returns 409 {"error":"settings.notConfigured"} when a use case needs
 * ProcessSettings but nothing has been saved yet - surface that as a specific,
 * actionable message (with a link to Settings) instead of a generic fetch-failed one.
 */
export function settingsAwareErrorKey(error: HttpErrorResponse, fallbackKey: string): string {
  return error.status === 409 && error.error?.error === 'settings.notConfigured'
    ? 'common.settingsNotConfigured'
    : fallbackKey;
}
