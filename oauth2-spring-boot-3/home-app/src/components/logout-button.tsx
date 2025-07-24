'use client';

import { getUserManager } from "@/util/oidc.client";

export const LogoutButton = () => {
    const handleLogout = () => {
        getUserManager().signoutRedirect();
    };

    return <button
        className="rounded-full border border-solid border-transparent transition-colors flex items-center justify-center bg-foreground text-background gap-2 hover:bg-[#383838] dark:hover:bg-[#ccc] font-medium text-sm sm:text-base h-10 sm:h-12 px-4 sm:px-5 sm:w-auto"
        onClick={handleLogout}>
        Logout
    </button>;
};