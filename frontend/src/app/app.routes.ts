import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './route.guards';
import { DashboardComponent } from './dashboard.component';
import { LandingComponent } from './landing.component';
import { ModuleComponent } from './module.component';
import { MessagePageComponent } from './message-page.component';
import { NutritionComponent } from './nutrition.component';

const moduleRoles = ['ADMIN', 'COACH', 'MEMBER'];
const nutritionRoles = ['ADMIN', 'MEMBER'];

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
    component: ModuleComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Training', module: 'M3', description: 'Training plans and coaching workspace.', roles: moduleRoles }
  },
  {
    path: 'nutrition',
    component: NutritionComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Nutrition', module: 'M4', description: 'Nutrition plans and member resources.', roles: nutritionRoles }
  },
  { path: 'forbidden', component: MessagePageComponent, data: { title: 'Access denied', message: 'Your account does not have permission to view this page.' } },
  { path: 'not-found', component: MessagePageComponent, data: { title: 'Page not found', message: 'The page you requested does not exist.' } },
  { path: '**', redirectTo: 'not-found' }
];
