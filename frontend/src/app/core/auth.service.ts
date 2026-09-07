import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'pvopt.basicAuthCredentials';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly isAuthenticated = signal(this.readStoredHeader() !== null);

  login(username: string, password: string): void {
    const header = 'Basic ' + btoa(`${username}:${password}`);
    sessionStorage.setItem(STORAGE_KEY, header);
    this.isAuthenticated.set(true);
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.isAuthenticated.set(false);
  }

  authorizationHeader(): string | null {
    return this.readStoredHeader();
  }

  private readStoredHeader(): string | null {
    try {
      return sessionStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
