import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './route.guards';
import { DashboardComponent } from './dashboard.component';
import { LandingComponent } from './landing.component';
import { ModuleComponent } from './module.component';
import { MessagePageComponent } from './message-page.component';

const moduleRoles = ['ADMIN', 'COACH', 'MEMBER'];

export const appRoutes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  {
    path: 'membership',
    component: ModuleComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Membership', module: 'M1', description: 'Your membership, reservations, and assigned-member access.', roles: moduleRoles }
  },
  {
    path: 'courses',
    component: ModuleComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Courses', module: 'M2', description: 'Explore gym courses and class schedules.', roles: moduleRoles }
  },
  {
    path: 'training',
    loadComponent: () => import('./training/training.component').then((module) => module.TrainingComponent),
    canActivate: [authGuard, roleGuard],
    data: { title: 'Training', module: 'M3', description: 'Training plans and coaching workspace.', roles: moduleRoles }
  },
  {
    path: 'training/programmes',
    loadComponent: () => import('./training/programmes.component').then((module) => module.ProgrammesComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COACH', 'MEMBER'] }
  },
  {
    path: 'training/workouts',
    loadComponent: () => import('./training/workouts.component').then((module) => module.WorkoutsComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MEMBER'] }
  },
  {
    path: 'nutrition',
    component: ModuleComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Nutrition', module: 'M4', description: 'Nutrition plans and member resources.', roles: moduleRoles }
  },
  { path: 'forbidden', component: MessagePageComponent, data: { title: 'Access denied', message: 'Your account does not have permission to view this page.' } },
  { path: 'not-found', component: MessagePageComponent, data: { title: 'Page not found', message: 'The page you requested does not exist.' } },
  { path: '**', redirectTo: 'not-found' }
];
