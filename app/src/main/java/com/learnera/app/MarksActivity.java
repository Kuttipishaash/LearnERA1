package com.learnera.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.learnera.app.data.User;
import com.learnera.app.fragments.MarksFragment;

/**
 * Created by Prejith on 8/8/2016.
 */
public class MarksActivity extends AppCompatActivity {

    private static FragmentTransaction fragmentTransaction;
    private Fragment fragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marks);
        if (Utils.isNetworkAvailable(this)) {
            if(User.isLoggedIn(this)) {
                doWhenNetworkPresent();
            }
            else {
                Utils.doWhenNotLoggedIn(this);
            }
        } else {
            Utils.doWhenNoNetwork(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_attendance, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case (R.id.action_marks_help):
                showHelp();
                return true;
            case (R.id.action_logout):
                User.logout(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void showHelp() {
        AlertDialog.Builder alert = new AlertDialog.Builder(MarksActivity.this);
        alert.setTitle(R.string.action_announcement_help)
                .setMessage(R.string.help_marks_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }


    @Override
    protected void onResume() {
        super.onResume();

    }

    public void doWhenNetworkPresent() {
        fragment = new MarksFragment();
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.marks_fragment, fragment);
        fragmentTransaction.commit();
    }

}