'use client';

import { LogoutButton } from "@/components/logout-button";
import Profile from "@/components/profile";
import { getUserManager } from "@/util/oidc.client";
import { useEffect, useState } from "react";
import { redirect } from 'next/navigation'

export default function Page() {

    const [user, setUser] = useState<any>(null);

    useEffect(() => {
        getUserManager().getUser().then((user) => {
            if (user && !user.expired) {
                setUser(user.profile);
            } else {
                redirect('/');
            }
        });
    }, []);

    return <div className="font-sans grid grid-rows-[20px_1fr_20px] items-center justify-items-center min-h-screen p-8 pb-20 gap-16 sm:p-20">
        <main className="flex flex-col gap-[32px] row-start-2 items-center sm:items-start">
            <p>Dashboard Page</p>
            {user &&
                <div>
                    <Profile />
                    <LogoutButton />
                </div>
            }
        </main>
    </div >;
}