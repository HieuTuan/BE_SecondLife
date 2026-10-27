package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.enums.AccountStatus;

import java.util.UUID;

/** Current security facts, without loading profiles or permission collections. */
public interface UserSecurityState {
    UUID getId();
    String getEmail();
    AccountStatus getAccountStatus();
    boolean getEmailVerified();
    long getTokenVersion();
    long getPermissionsVersion();
}
