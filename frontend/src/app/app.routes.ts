import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './route.guards';
import { DashboardComponent } from './dashboard.component';
import { LandingComponent } from './landing.component';
import { ModuleComponent } from './module.component';
import { FeedbackComponent } from './feedback.component';
import { MembershipComponent } from './membership.component';
import { MessagePageComponent } from './message-page.component';
import { NutritionWorkspaceComponent } from './nutrition-workspace.component';
import { CoachNutritionComponent } from './coach-nutrition.component';

const moduleRoles = ['ADMIN', 'COACH', 'MEMBER'];
const nutritionRoles = ['ADMIN', 'MEMBER'];

export const appRoutes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  {
    path: 'membership',
    component: MembershipComponent,
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
    component: NutritionWorkspaceComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Nutrition', module: 'M4', description: 'Nutrition plans and member resources.', roles: nutritionRoles }
  },
  {
    path: 'coach/nutrition',
    component: CoachNutritionComponent,
    canActivate: [authGuard, roleGuard],
    data: { title: 'Coach Nutrition', module: 'M4', description: 'Review and edit meal suggestions for assigned members.', roles: ['COACH'] }
  },
  {
    path: 'feedback',
    component: FeedbackComponent,
    canActivate: [authGuard]
  },
  { path: 'forbidden', component: MessagePageComponent, data: { title: 'Access denied', message: 'Your account does not have permission to view this page.' } },
  { path: 'not-found', component: MessagePageComponent, data: { title: 'Page not found', message: 'The page you requested does not exist.' } },
  { path: '**', redirectTo: 'not-found' }
];
