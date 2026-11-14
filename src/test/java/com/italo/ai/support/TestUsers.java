package com.italo.ai.support;

import com.italo.ai.user.UserEntity;

public final class TestUsers {
    public static final UserEntity ALICE = new UserEntity("alice", 5123456789L);
    public static final UserEntity BOB = new UserEntity("bob", null);
    public static final UserEntity ITALO = new UserEntity("italo", null);

    private TestUsers() {}
}
