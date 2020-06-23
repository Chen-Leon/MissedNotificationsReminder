package com.app.missednotificationsreminder.ui

import com.app.missednotificationsreminder.ui.activity.MainActivity
import dagger.Module

/**
 * The Dagger dependency injection module for the UI layer
 */
@Module(includes = [MainActivity.Module::class, UiModuleExt::class])
class UiModule