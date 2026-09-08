import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./login/login').then((m) => m.Login)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard)
  },
  {
    path: 'history',
    canActivate: [authGuard],
    loadComponent: () => import('./history/history').then((m) => m.History)
  },
  {
    path: 'tou-schedule',
    canActivate: [authGuard],
    loadComponent: () => import('./tou-schedule/tou-schedule').then((m) => m.TouSchedule)
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./settings/settings').then((m) => m.Settings)
  },
  { path: '**', redirectTo: 'dashboard' }
];
