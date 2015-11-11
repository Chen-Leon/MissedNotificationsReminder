package com.app.missednotificationsreminder.binding.model;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;

import com.app.missednotificationsreminder.binding.util.BindableBoolean;
import com.app.missednotificationsreminder.binding.util.RxBindingUtils;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import timber.log.Timber;

/**
 * The view model for the single application item
 *
 * @author Eugene Popovich
 */
public class ApplicationItemViewModel extends BaseViewModel{
    /**
     * Data binding field to store application checked state
     */
    public BindableBoolean checked = new BindableBoolean();

    private ApplicationCheckedStateChangedListener mApplicationCheckedStateChangedListener;
    private PackageInfo mPackageInfo;
    private Picasso mPicasso;
    private PackageManager mPackageManager;

    /**
     * @param checked                                the current application checked state
     * @param packageInfo                            the application package info
     * @param packageManager
     * @param picasso
     * @param applicationCheckedStateChangedListener the listener to subscribe to the on checked
     *                                               state changed event
     */
    public ApplicationItemViewModel(
            boolean checked, PackageInfo packageInfo,
            PackageManager packageManager, Picasso picasso,
            ApplicationCheckedStateChangedListener applicationCheckedStateChangedListener) {
        Timber.d("Constructor");
        mPackageInfo = packageInfo;
        mPackageManager = packageManager;
        mPicasso = picasso;
        mApplicationCheckedStateChangedListener = applicationCheckedStateChangedListener;
        this.checked.set(checked);
        if (mApplicationCheckedStateChangedListener != null) {
            monitor(RxBindingUtils
                    .valueChanged(this.checked)
                    .skip(1) // skip the current value processing, which is passed automatically
                    .subscribe(value -> {
                        Timber.d("Checked property changed for %1$s", toString());
                        mApplicationCheckedStateChangedListener.onApplicationCheckedStateChanged
                                (mPackageInfo, value);
                    }));
        }
    }

    /**
     * Get the application name
     *
     * @return
     */
    public CharSequence getName() {
        Timber.d("getName for %1$s", toString());
        return mPackageInfo.applicationInfo.loadLabel(mPackageManager);
    }

    /**
     * Get the application description
     *
     * @return
     */
    public String getDescription() {
        Timber.d("getDescription for %1$s", toString());
        return mPackageInfo.packageName;
    }

    /**
     * Get the application icon request
     *
     * @return
     */
    public RequestCreator getIcon() {
        Timber.d("getIcon for %1$s", toString());
        Uri result = null;
        int icon = mPackageInfo.applicationInfo.icon;
        if (icon != 0) {
            result = Uri.parse("android.resource://" + mPackageInfo.packageName + "/" + icon);
        }
        return result == null ? null : mPicasso.load(result)
                .fit();
    }

    /**
     * Reverse checked state. Called when the application item clicked. Method binded directly in
     * the layout xml
     *
     * @param v
     */
    public void onItemClicked(View v) {
        Timber.d("onItemClicked for %1$s", toString());
        checked.set(!checked.get());
    }

    @Override
    public String toString() {
        return String.format("%1$s(checked=%2$b, package=%3$s)", getClass().getSimpleName(), checked.get(), mPackageInfo.packageName);
    }

    /**
     * The interface subscribers to the onApplicationCheckedStateChanged event should implement
     */
    public static interface ApplicationCheckedStateChangedListener {
        void onApplicationCheckedStateChanged(PackageInfo packageInfo, boolean checked);
    }
}
