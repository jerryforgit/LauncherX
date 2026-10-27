package com.jerry.launcherx.search.algorithms.engine

import android.content.Context

interface SearchPermission {
    fun checkPermission(context: Context): Any
}
