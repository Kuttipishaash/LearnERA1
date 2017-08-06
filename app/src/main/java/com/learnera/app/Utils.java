package com.learnera.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.Toast;

import com.learnera.app.fragments.LoginFragment;
import com.learnera.app.fragments.NetworkNotAvailableFragment;

/**
 * Created by praji on 7/4/2017.
 */

public class Utils {

    //To check internet connection
    public static boolean isNetworkAvailable(FragmentActivity fragmentActivity) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) fragmentActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    //Used to open network not available fragment whenever network is not present
    public static void doWhenNoNetwork(FragmentActivity fragmentActivity) {
        Fragment fragment = new NetworkNotAvailableFragment();
        FragmentManager fragmentManager = fragmentActivity.getSupportFragmentManager();
        android.support.v4.app.FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (fragmentActivity instanceof MarksActivity) {
            //fragmentTransaction.addToBackStack("marksfrag");
            fragmentTransaction.replace(R.id.marks_fragment, fragment);
        } else if (fragmentActivity instanceof AttendanceActivity) {
            //fragmentTransaction.addToBackStack("attendfrag");
            fragmentTransaction.replace(R.id.fragment_attendance, fragment);
        } else if (fragmentActivity instanceof LoginActivity) {
            //fragmentTransaction.addToBackStack("loginfrag");
            fragmentTransaction.replace(R.id.fragment_login, fragment);
        } else if (fragmentActivity instanceof AnnouncementsActivity) {
            AnnouncementsActivity.network.setVisibility(View.VISIBLE);
            AnnouncementsActivity.mViewPager.setVisibility(View.GONE);
            AnnouncementsActivity.tabLayout.setVisibility(View.GONE);
        }
        fragmentTransaction.commit();
    }

    public static void doWhenNotLoggedIn(FragmentActivity fragmentActivity) {
        Toast.makeText(fragmentActivity, "Please login first", Toast.LENGTH_SHORT).show();
        Fragment fragment = new LoginFragment();
        FragmentTransaction fragmentTransaction = fragmentActivity.getSupportFragmentManager().beginTransaction();
        if(fragmentActivity instanceof AttendanceActivity)
            fragmentTransaction.replace(R.id.fragment_attendance, fragment);
        else if(fragmentActivity instanceof MarksActivity)
            fragmentTransaction.replace(R.id.marks_fragment, fragment);
        fragmentTransaction.commit();
    }
}
