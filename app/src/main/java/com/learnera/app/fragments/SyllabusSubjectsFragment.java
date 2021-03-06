package com.learnera.app.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.learnera.app.BuildConfig;
import com.learnera.app.R;
import com.learnera.app.activities.WelcomeActivity;
import com.learnera.app.adapters.SyllabusSubjectAdapter;
import com.learnera.app.database.LearnEraRoomDatabase;
import com.learnera.app.database.dao.SubjectDetailDAO;
import com.learnera.app.models.Constants;
import com.learnera.app.models.SubjectDetail;
import com.learnera.app.models.User;
import com.learnera.app.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.learnera.app.models.Constants.Firebase.REMOTE_CONFIG_DEFAULT_SYLLABUS_VERSION;
import static com.learnera.app.models.Constants.Firebase.REMOTE_CONFIG_SYLLABUS_VERSION;


public class SyllabusSubjectsFragment extends Fragment implements AdapterView.OnItemSelectedListener {

    // Constants
    private static final String TAG = "SyllabusSubjectsFrag";
    private static final long REMOTE_CONFIG_CACHE_EXPIRATION_IN_SEC = 43200L;   // New remote config values will be fetched every 12 hours.
    private static final long REMOTE_CONFIG_CACHE_EXPIRATION_IN_SEC_DEV = 5;   // New remote config values will be fetched every 12 hours.
    //Shared Preferences
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    private User mCurrentUser = new User();
    // Views
    private View view;
    private RecyclerView mSubjectsRecyclerView;
    private Spinner mSemesterSelectSpinner;
    // Firebase
    private FirebaseRemoteConfig mRemoteConfig = FirebaseRemoteConfig.getInstance();
    private FirebaseDatabase mFirebaseDatabase = FirebaseDatabase.getInstance();
    private SubjectDetailDAO subjectDetailDAO;
    private long localSyllabusVersion;
    private long fetchedSyllabusVersion;
    private ProgressDialog mProgressDialog;
    private int currentSem;

    public SyllabusSubjectsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_syllabus_subjects, container, false);
        subjectDetailDAO = LearnEraRoomDatabase.getDatabaseInstance(getActivity()).subjectDetailDAO();

        initViews();
        initProgressDialog();
        initToolbar();

        // Getting current user info
        mCurrentUser = User.getLoginInfo(getActivity());
//        mProgressDialog.show();
        sharedPreferences = getActivity().getSharedPreferences(Constants.PREFERENCE_FILE, Context.MODE_PRIVATE);
        setSemesterSpinnerContents();
        checkSyllabusUpdates();
        return view;
    }

    private void checkSyllabusUpdates() {
        Log.e(TAG, "Current remote config value : " + mRemoteConfig.getLong(REMOTE_CONFIG_SYLLABUS_VERSION));
        // Firebase RemoteConfig setup
        mRemoteConfig.setConfigSettings(new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build());
        HashMap<String, Object> defaults = new HashMap<>();
        defaults.put(REMOTE_CONFIG_SYLLABUS_VERSION, REMOTE_CONFIG_DEFAULT_SYLLABUS_VERSION);
        mRemoteConfig.setDefaults(defaults);


        localSyllabusVersion = mRemoteConfig.getLong(REMOTE_CONFIG_SYLLABUS_VERSION);
        fetchedSyllabusVersion = localSyllabusVersion;
        //TODO: CHANGE THE TIMEOUT BEFORE PRODUCTION
        final Task<Void> fetch = mRemoteConfig.fetch(REMOTE_CONFIG_CACHE_EXPIRATION_IN_SEC_DEV);
        fetch.addOnCompleteListener(this.getActivity(), new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    //TODO: Remove debug messages
                    mRemoteConfig.activateFetched();
                    fetchedSyllabusVersion = mRemoteConfig.getLong(REMOTE_CONFIG_SYLLABUS_VERSION);
                    if (fetchedSyllabusVersion > localSyllabusVersion) {
                        Log.e(TAG, "onComplete: Remote config fetched and new version is available.");
                        mProgressDialog.show();
                        updateSubjects();
                    }
                    Log.e(TAG, "RemoteConfig fetch successful");
                    Log.e(TAG, "New value : " + mRemoteConfig.getLong(REMOTE_CONFIG_SYLLABUS_VERSION));
                } else {
                    Log.e(TAG, "RemoteConfig fetch failed");
                    if (fetchedSyllabusVersion > REMOTE_CONFIG_DEFAULT_SYLLABUS_VERSION) {
                        if (sharedPreferences.getBoolean(getActivity().getString(R.string.pref_syllabus_outdated), true)) {
                            Log.e(TAG, "Syllabus not fully fetched to offline db");
                            if (Utils.isNetworkAvailable(getActivity())) {
                                //If syllabus is not fully fetched then fetch it to offline db if there is network connection
                                Log.e(TAG, "onComplete: Not fully fetched to db");
                                mProgressDialog.show();
                                updateSubjects();
                            } else {
                                //If syllabus is not fully fetched and no network connection is available
                                Log.e(TAG, "onComplete: Not fully fetched and network not available");
                                mProgressDialog.hide();
                                Utils.doWhenNoNetwork(getActivity());
                            }
                        } else {
                            //If offline content is fully available
                            Log.e(TAG, "onComplete: Full contents available offline");
                            setRecyclerViewContents();
                            mProgressDialog.hide();
                        }
                    } else {
                        //Syllabus was not even fetched to offline even for a single time
                        mProgressDialog.hide();
                        Utils.doWhenNoNetwork(getActivity());
                        Log.e(TAG, "Syllabus not fetched even once");
                    }
                }
            }
        });
    }

    //Initializing views
    private void initViews() {
        mSubjectsRecyclerView = view.findViewById(R.id.subjects_rec_view_frg_syl);
        mSemesterSelectSpinner = view.findViewById(R.id.spin_semester_frg_syl);
    }

    private void initProgressDialog() {
        mProgressDialog = new ProgressDialog(view.getContext(), R.style.ProgressDialogCustom);
        mProgressDialog.setMessage(getString(R.string.msg_loading_syllabus));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                startActivity(new Intent(getActivity(), WelcomeActivity.class));
            }
        });
    }

    private void initToolbar() {
        Toolbar toolbar = view.findViewById(R.id.toolbar_syllabus);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Syllabus");
    }

    private void updateSubjects() {
        editor = sharedPreferences.edit();
        editor.putBoolean(getActivity().getString(R.string.pref_syllabus_outdated), true);
        editor.apply();
        // Fetching data from Firebase Realtime Database and storing it to the local RoomDatabase
        final DatabaseReference databaseReference = mFirebaseDatabase.getReference(getString(R.string.firebase_syllabus_fetch_path));
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.e(TAG, "onDataChange is executing");
                subjectDetailDAO.deleteAll();
                for (DataSnapshot branchSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot semesterSnapshot : branchSnapshot.getChildren()) {
                        for (DataSnapshot subjectDetailsSnapshot : semesterSnapshot.getChildren()) {
                            SubjectDetail subject = subjectDetailsSnapshot.getValue(SubjectDetail.class);
                            if (subject != null) {
                                subject.setBranch(branchSnapshot.getKey());
                                subject.setSemester(Integer.parseInt(semesterSnapshot.getKey()));
                                subjectDetailDAO.insertSubject(subject);
                            }
                        }
                    }
                }
                setRecyclerViewContents();
                mProgressDialog.hide();
                editor = sharedPreferences.edit();
                editor.putBoolean(getActivity().getString(R.string.pref_syllabus_outdated), false);
                editor.apply();
                Log.e(TAG, "onDataChange has finished executing");

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                editor = sharedPreferences.edit();
                editor.putBoolean(getActivity().getString(R.string.pref_syllabus_outdated), true);
                editor.apply();
                mProgressDialog.hide();
                Utils.doWhenNoNetwork(getActivity());
            }
        });
    }


    // Function to setup spinner to select semester
    private void setSemesterSpinnerContents() {

        currentSem = mCurrentUser.getSem() - 1;
        ArrayList<String> semList = new ArrayList<>();
        for (int i = 0; i <= 7; i++) {
            semList.add(getResources().getStringArray(R.array.array_semesters)[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                view.getContext(),
                android.R.layout.simple_spinner_item,
                semList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSemesterSelectSpinner.setAdapter(adapter);
        mSemesterSelectSpinner.setSelection(currentSem);
        mSemesterSelectSpinner.setOnItemSelectedListener(this);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        //Getting subject names to display in list
        currentSem = position + 1;
        setRecyclerViewContents();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void setRecyclerViewContents() {
        //Getting subject names
        List<SubjectDetail> subjectsList;
        String currentDept = mCurrentUser.getDept();
        if (currentSem == 1 || currentSem == 2) {
            subjectsList = subjectDetailDAO.getSubjects(0, currentDept);
        } else {
            subjectsList = subjectDetailDAO.getSubjects(currentSem - 2, currentDept);
        }
        //Setting list view and adapters
        SyllabusSubjectAdapter syllabusSubjectAdapter = new SyllabusSubjectAdapter();
        syllabusSubjectAdapter.setmSubjectDetailsList(subjectsList);
        mSubjectsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mSubjectsRecyclerView.setAdapter(syllabusSubjectAdapter);
        syllabusSubjectAdapter.notifyDataSetChanged();
        Log.e(TAG, "setRecyclerViewContents: hiding progress bar");
    }
}
