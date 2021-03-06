/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd;

import android.os.StrictMode;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.multidex.MultiDexApplication;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import com.akaita.java.rxjava2debug.RxJava2Debug;
import com.google.android.gnd.rx.RxDebug;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import dagger.hilt.android.HiltAndroidApp;
import io.reactivex.plugins.RxJavaPlugins;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

@HiltAndroidApp
public class GndApplication extends MultiDexApplication implements Configuration.Provider {

  @Inject HiltWorkerFactory workerFactory;

  public GndApplication() {
    super();
    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());
    } else {
      Timber.plant(new CrashReportingTree());
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    if (BuildConfig.DEBUG) {
      Timber.d("DEBUG build config active; enabling debug tooling");

      // Log failures when trying to do work in the UI thread.
      setStrictMode();
    }

    // Enable RxJava assembly stack collection for more useful stack traces.
    RxJava2Debug.enableRxJava2AssemblyTracking(new String[] {getClass().getPackage().getName()});

    // Prevent RxJava from force-quitting on unhandled errors. Defining an error handler is
    // necessary even if all errors are handled to avoid UndeliverableException when errors are
    // triggered on streams with all subscriptions disposed. Read more:
    // https://medium.com/@bherbst/the-rxjava2-default-error-handler-e50e0cab6f9f
    RxJavaPlugins.setErrorHandler(RxDebug::logEnhancedStackTrace);

    WorkManager.initialize(getApplicationContext(), getWorkManagerConfiguration());
  }

  @Override
  @NotNull
  public Configuration getWorkManagerConfiguration() {
    return new Configuration.Builder().setWorkerFactory(workerFactory).build();
  }

  private void setStrictMode() {
    StrictMode.setThreadPolicy(
        new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

    StrictMode.setVmPolicy(
        new StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects().penaltyLog().build());
  }

  private static class CrashReportingTree extends Timber.Tree {
    @Override
    protected void log(int priority, String tag, @NonNull String message, Throwable throwable) {
      if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
        return;
      }

      FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
      crashlytics.log(message);

      if (throwable != null && priority == Log.ERROR) {
        crashlytics.recordException(throwable);
      }
    }
  }
}
