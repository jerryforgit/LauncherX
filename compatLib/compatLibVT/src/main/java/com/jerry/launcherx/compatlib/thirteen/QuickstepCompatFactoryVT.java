package com.jerry.launcherx.compatlib.thirteen;

import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.jerry.launcherx.compatlib.ActivityManagerCompat;
import com.jerry.launcherx.compatlib.ActivityOptionsCompat;
import com.jerry.launcherx.compatlib.RemoteTransitionCompat;
import com.jerry.launcherx.compatlib.twelve.QuickstepCompatFactoryVS;

@RequiresApi(33)
public class QuickstepCompatFactoryVT extends QuickstepCompatFactoryVS {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVT();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVT();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return (remoteTransition, appThread, debugName) ->
                new RemoteTransition(remoteTransition, appThread);
    }
}
