package com.jerry.launcherx.compatlib;

import androidx.annotation.NonNull;

public interface QuickstepCompatFactory {

    @NonNull
    ActivityManagerCompat getActivityManagerCompat();

    @NonNull
    ActivityOptionsCompat getActivityOptionsCompat();

    @NonNull
    RemoteTransitionCompat getRemoteTransitionCompat();
}
