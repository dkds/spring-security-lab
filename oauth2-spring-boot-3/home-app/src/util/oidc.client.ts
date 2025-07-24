import { UserManager } from 'oidc-client-ts';
import { oidcConfig } from './oidc.config';

let userManager: UserManager;

export function getUserManager(): UserManager {
    if (!userManager) {
        userManager = new UserManager(oidcConfig);
    }
    return userManager;
}
