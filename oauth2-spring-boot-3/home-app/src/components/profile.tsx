'use client';

import { getUserManager } from '@/util/oidc.client';
import { useEffect, useState } from 'react';

const Profile = () => {
    const [user, setUser] = useState<any>(null);

    useEffect(() => {
        getUserManager().getUser().then((user) => {
            if (user && !user.expired) {
                setUser(user.profile);
            }
        });
    }, []);

    if (!user) return <div>Not logged in</div>;

    return (
        <div>
            <h2>Welcome, {user.name}</h2>
            <pre>{JSON.stringify(user, null, 2)}</pre>
        </div>
    );
};

export default Profile;
