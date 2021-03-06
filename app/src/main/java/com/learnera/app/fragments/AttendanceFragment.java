package com.learnera.app.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.learnera.app.R;
import com.learnera.app.adapters.AttendanceAdapter;
import com.learnera.app.adapters.AttendanceTableAdapter;
import com.learnera.app.anim.MyBounceInterpolator;
import com.learnera.app.database.LearnEraRoomDatabase;
import com.learnera.app.database.dao.AttendanceDAO;
import com.learnera.app.models.AttendanceDetails;
import com.learnera.app.models.AttendanceTableCells;
import com.learnera.app.models.AttendanceTableRow;
import com.learnera.app.models.Constants;
import com.learnera.app.models.SharedViewModel;
import com.learnera.app.models.User;
import com.learnera.app.utils.AKDialogFragment;
import com.learnera.app.utils.Utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Prejith on 6/30/2017.
 */

// TODO: 7/31/2017 Code to be optimized and minor bugs to be fixed.

public class AttendanceFragment extends Fragment implements AdapterView.OnItemSelectedListener {

    private static final String TAG = "ATTENDANCE_ACTIVITY";
    final protected String sub = "Total Class";
    public ArrayList<AttendanceTableRow> tableRows;
    protected int pos;
    protected String code;
    protected Document studentsCorenerDoc;
    protected Document parentsCornerDoc;
    protected Connection.Response res;
    protected Connection.Response resParentsCorner;
    protected Pattern codePattern, singlePattern, threePattern;
    protected ArrayAdapter<String> mSpinnerAdapter;
    protected Spinner spinner;
    protected TextView offlineWarningView;
    //For attendance Table
    protected FloatingActionButton fab;
    protected AttendanceTableAdapter tableAdapter;
    JSoupAttendanceTask jSoupAttendanceTask;
    JSoupSpinnerTask jSoupSpinnerTask;
    SharedPreferences sharedPreferences;
    //anim
    Animation fadeInAnimation;
    Animation fadeOutAnimation;
    boolean isFabHide = false;
    private SharedViewModel sharedViewModel;
    //offline
    private AttendanceDAO attendanceDAO;
    private List<AttendanceDetails> attendance;
    private AttendanceDetails details;
    private CoordinatorLayout coordinatorLayout;
    private Snackbar snackbar;
    //To remove
    private ProgressDialog mProgressDialog;
    private int count;
    private View view;
    private User user;
    //For Recycler
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mRecyclerAdapter;
    private List<String> mSubjectList;
    private List<String> mPercentageList;
    private List<String> mSubjectCodeList;
    private List<String> mMissedList;
    private List<String> mTotalList;
    private List<String> mDutyAttendenceList;   //For duty attendence count for each subject
    //For Spinner
    private ArrayList<String> mSemesters;
    private ArrayList<String> mSemesterList;
    //For setting cutoff percentage
    private RadioGroup attendancePercentSelector;
    //For enabling/disabling on duty
    private RadioGroup dutyEnablerSelector;
    private JSoupDutyLeaveTask jSoupDutyLeaveTask;

    public AttendanceFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = User.getLoginInfo(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_attendance, container, false);

        spinner = view.findViewById(R.id.spinner_attendance);
        offlineWarningView = view.findViewById(R.id.attd_offline_warning);
        spinner.setOnItemSelectedListener(this);

        fadeInAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fadein);
        fadeOutAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.fadeout);

        dutyEnablerSelector = view.findViewById(R.id.attendance_duty_selector);

        mRecyclerView = view.findViewById(R.id.recycler_view_attendance);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getActivity(),
                DividerItemDecoration.VERTICAL));
        initProgressDialog();

        mSemesters = new ArrayList<>();


        //Semester list not included as semesters shouldn't be initalised in both the calls of initLists
        mSemesterList = new ArrayList<>();
        initLists();

        //initiate patterns for matching string
        initPatterns();

        attendanceDAO = LearnEraRoomDatabase.getDatabaseInstance(getActivity()).attendanceDAO();
        int offlineAttendanceSize = attendanceDAO.getAttendance().size();
        coordinatorLayout = view.findViewById(R.id.layout_attendance_root);

        if (!Utils.isNetworkAvailable(getActivity()) && offlineAttendanceSize > 0) {
            offlineWarningView.setVisibility(View.VISIBLE);
            showOfflineData();
            spinner.setVisibility(View.GONE);
            sharedPreferences = getActivity().getSharedPreferences(Constants.DATE_UPDATE_ATTENDANCE, Context.MODE_PRIVATE);
            String date = sharedPreferences.getString("date", "");
            snackbar = Snackbar.make(coordinatorLayout, "You are viewing offline data last updated on " + date, Snackbar.LENGTH_LONG);
            View sbView = snackbar.getView();
            sbView.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.md_red_800));
            snackbar.show();
        } else {
            spinner.setVisibility(View.VISIBLE);
            jSoupSpinnerTask = new JSoupSpinnerTask();
            jSoupSpinnerTask.execute();

            //check for internet connectivity
            Handler handler = new Handler();
            Utils.testInternetConnectivity(jSoupSpinnerTask, handler);
        }

        //For attendance details
        fab = view.findViewById(R.id.attendance_fab);
        final Animation myAnim = AnimationUtils.loadAnimation(getContext(), R.anim.bounce);
        MyBounceInterpolator interpolator = new MyBounceInterpolator(0.2, 20);
        myAnim.setInterpolator(interpolator);

        fab.startAnimation(myAnim);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tableAdapter = new AttendanceTableAdapter(getActivity(), tableRows);
                sharedViewModel = ViewModelProviders.of(getActivity()).get(SharedViewModel.class);
                sharedViewModel.set(tableAdapter);
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                AKDialogFragment newFragment = new AKDialogFragment();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                transaction.add(android.R.id.content, newFragment).addToBackStack(null).commit();
            }
        });

        setRadioButtons();

        initToolbar();
        initComponent();

        return view;
    }

    public void showOfflineData() {
        //get values from db
        attendance = attendanceDAO.getAttendance();

        //assign data from room into lists
        if (attendance.size() != 0) {
            int pos = attendance.size() - 1;

            mMissedList = attendance.get(pos).getMissedList();
            mSubjectCodeList = attendance.get(pos).getSubjectCodeList();
            mDutyAttendenceList = attendance.get(pos).getDutyAttendanceList();
            mSubjectList = attendance.get(pos).getSubjectList();
            mPercentageList = attendance.get(pos).getPercentageList();
            mTotalList = attendance.get(pos).getTotalList();
            tableRows = attendance.get(pos).getTableRows();

            //get current date and month
            int monthNumber = Calendar.getInstance().get(Calendar.MONTH);
            int date = Calendar.getInstance().get(Calendar.DATE);

            // save timestamp in preference file
            sharedPreferences = getContext().getSharedPreferences(Constants.DATE_UPDATE_ATTENDANCE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.putString("date", date + getDateSuffix(date) + " " + calculateMonth(monthNumber));
            editor.apply();

            dutyEnablerSelector.check(R.id.attendance_duty_disable);
            populateList(false);
        }
    }

    private String getDateSuffix(final int n) {
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }

    private String calculateMonth(int monthNumber) {
        String month;
        switch (monthNumber) {
            case 0:
                month = "January";
                break;
            case 1:
                month = "February";
                break;
            case 2:
                month = "March";
                break;
            case 3:
                month = "April";
                break;
            case 4:
                month = "May";
                break;
            case 5:
                month = "June";
                break;
            case 6:
                month = "July";
                break;
            case 7:
                month = "August";
                break;
            case 8:
                month = "September";
                break;
            case 9:
                month = "October";
                break;
            case 10:
                month = "November";
                break;
            case 11:
                month = "December";
                break;
            default:
                month = "";
                break;
        }
        return month;
    }

    private void initToolbar() {
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getString(R.string.title_activity_attendance));
    }

    private void initComponent() {
        NestedScrollView nested_content = view.findViewById(R.id.nested_scroll_view);
        nested_content.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY < oldScrollY) { // up
                    animateFab(false);
                }
                if (scrollY > oldScrollY) { // down
                    animateFab(true);
                }
            }
        });
    }

    private void animateFab(final boolean hide) {
        FloatingActionButton fab_add = view.findViewById(R.id.attendance_fab);
        if (isFabHide && hide || !isFabHide && !hide) return;
        isFabHide = hide;
        int moveY = hide ? (2 * fab_add.getHeight()) : 0;
        fab_add.animate().translationY(moveY).setStartDelay(100).setDuration(300).start();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        //Extract semester code from spinner selection
        pos = spinner.getSelectedItemPosition();
        code = mSemesterList.get(pos);

        //Start populating recycler view
        jSoupAttendanceTask = new JSoupAttendanceTask();
        jSoupAttendanceTask.execute();



        //check for internet connectivity
        Handler handler = new Handler();
        Utils.testInternetConnectivity(jSoupAttendanceTask, handler);
        Handler handlerDutyAttendance = new Handler();
        Utils.testInternetConnectivity(jSoupAttendanceTask, handler);


    }

    public void cancelAsyncTasks() {
        if (jSoupAttendanceTask != null) {
            jSoupAttendanceTask.cancel(true);
            try {
                jSoupAttendanceTask.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            jSoupAttendanceTask = null;
        } else if (jSoupSpinnerTask != null) {
            jSoupSpinnerTask.cancel(true);
            try {
                jSoupSpinnerTask.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            jSoupSpinnerTask = null;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void initProgressDialog() {
        mProgressDialog = new ProgressDialog(view.getContext(), R.style.ProgressDialogCustom);
        mProgressDialog.setMessage(getString(R.string.msg_loading_rsms_data));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
//        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
//            @Override
//            public void onCancel(DialogInterface dialog) {
//                startActivity(new Intent(getActivity(), WelcomeActivity.class));
//                getActivity().finish();
//            }
//        });
    }


    private void initLists() {
        mSubjectList = new ArrayList<>();
        mPercentageList = new ArrayList<>();
        mMissedList = new ArrayList<>();
        mTotalList = new ArrayList<>();
        mSubjectCodeList = new ArrayList<>();
        mDutyAttendenceList = new ArrayList<>();
    }

    private void clearLists() {
        mPercentageList.clear();
        mSubjectList.clear();
        mSubjectCodeList.clear();
        mMissedList.clear();
        mTotalList.clear();
    }

    private void initPatterns() {
        codePattern = Pattern.compile("\\w{2}\\d{3}");
        threePattern = Pattern.compile("[A-Z]{3}");
        singlePattern = Pattern.compile("[A-Z]");
    }

    private void extractAttendanceData() {
        Elements tables = studentsCorenerDoc.select("table [width=96%]");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements tds = rows.select("td");
                for (Element td : tds.select(":containsOwn(Total Class)")) {
                    String data = td.text();

                    mTotalList.add(data.substring(data.toLowerCase().indexOf(sub.toLowerCase())).replaceAll("[^\\d]", ""));

                }
                for (Element td : tds) {
                    String data = td.getElementsByTag("b").text();
                    String htmlData = td.html();

                    Matcher matcher = codePattern.matcher(htmlData);
                    Matcher matcher2 = threePattern.matcher(htmlData);
                    Matcher matcher3 = singlePattern.matcher(htmlData);

                    if (count > 1) {
                        if (matcher.find()) {
                            mSubjectCodeList.add(matcher.group());
                        } else if (matcher2.find()) {
                            mSubjectCodeList.add(matcher2.group());
                        } else if (matcher3.find()) {
                            mSubjectCodeList.add(matcher3.group());
                        }

                        if (!data.equals("")) {
                            mSubjectList.add(data);
                            mRecyclerAdapter.notifyItemInserted(mSubjectList.size());
                        }
                    }
                    count++;
                }

                for (Element td : tds) {
                    String data = td.select(":containsOwn(%)").text();
                    String data2 = td.getElementsByTag("strong").text();

                    if (td.text().equals("-")) {
                        int index = mPercentageList.size();
                        mTotalList.remove(index);
                        mSubjectCodeList.remove(index);
                        mSubjectList.remove(index);
                        continue;

                        //mPercentageList.add("-");
                        // mMissedList.add("-");
                    } else {
                        if (!data.equals("")) {
                            //Remove first 2 characters as they are invalid
                            StringBuilder build = new StringBuilder(data);
                            String printer = build.delete(0, 2).toString();
                            printer = printer.replaceAll("\\s+", "");

                            //Add to list
                            mPercentageList.add(printer);
                        }

                        if (!data2.equals("")) {
                            mMissedList.add(data2);
                        }
                    }
                }
                break;
            }
            break;
        }
    }

    private void setRadioButtons() {
        int attendanceCutoff = user.getattendanceCutoff(getActivity());

        attendancePercentSelector = view.findViewById(R.id.attendance_cutoff_selector);

        //set default value as disabled
        dutyEnablerSelector.check(R.id.attendance_duty_disable);

        //set value of cutoff from sharedpreferences which was loaded into attendanceCutOff
        switch (attendanceCutoff) {
            case 75:
                attendancePercentSelector.check(R.id.attendance_cutoff_75);
                break;
            case 60:
                attendancePercentSelector.check(R.id.attendance_cutoff_60);
                break;
        }


        //listener for cutoff radio group
        attendancePercentSelector.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i) {
                    case R.id.attendance_cutoff_75:
                        user.setAttendenceCutoff(getActivity(), 75);
                        if (dutyEnablerSelector.getCheckedRadioButtonId() == R.id.attendance_duty_disable) {
                            populateList(false);
                        } else {
                            populateList(true);
                        }
                        break;
                    case R.id.attendance_cutoff_60:
                        user.setAttendenceCutoff(getActivity(), 60);
                        if (dutyEnablerSelector.getCheckedRadioButtonId() == R.id.attendance_duty_disable) {
                            populateList(false);
                        } else {
                            populateList(true);
                        }
                        break;
                }
            }
        });

        //listener for duty attendance enabling and disabling radio group
        dutyEnablerSelector.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i) {
                    case R.id.attendance_duty_disable:
                        populateList(false);
                        break;
                    case R.id.attendance_duty_enable:
                        populateList(true);
                        break;
                }
            }
        });
    }

    private void extractSemesterList() {
        mSemesters.clear();
        Elements elements = studentsCorenerDoc.select("form");
        for (Element element : elements.select("option")) {
            mSemesterList.add(element.text());
            count++;
        }
        //Decrement count by 1 as extra empty input is being taken on scraping. This fixes the index 'count'
        count--;
        for (int i = 0; i <= count; i++) {
            mSemesters.add(getActivity().getResources().getStringArray(R.array.array_semesters)[i]);
        }
    }

    private void setDefaultCountValue() {
        count = 0;
    }

    private void attendanceDetails() {


        //To show table view of attendance of the days on which the student was absent
        tableRows = new ArrayList<AttendanceTableRow>();


        //Making the mDutyAttendenceList values to 0 for each subject initially
        //SubjectDetail whose subject codes doesnt end with a number i.e, V,SEP,LIB etc are removed as it will cause issues for duty attendance
        int loopvar = mSubjectList.size();
        for (int i = 0; i < loopvar; i++) {
            String subCode = mSubjectCodeList.get(i);
            subCode = subCode.substring(subCode.length() - 1); //taking last character of subject code
            boolean testResult = true;
            try {
                Integer.parseInt(subCode);  //trying to convert it to a number
            } catch (NumberFormatException e) {    //if it couldn't be converted to a number it is a subject to be removed so remove it from all other lists
                testResult = false;
                try{
                    mSubjectList.remove(i);
                    mPercentageList.remove(i);
                    mMissedList.remove(i);
                    mTotalList.remove(i);
                    mSubjectCodeList.remove(i);
                    loopvar--;
                    i--;
                }
                catch (IndexOutOfBoundsException indexException){
                    indexException.printStackTrace();
                    return;
                }
            }
            if (testResult)  //if it is an acceptable subjects i.e, it is not V,SEP,LIB etc, a 0 entry is made for the subject in the mDutyAttendanceList
                mDutyAttendenceList.add(Integer.toString(0));
        }


        //DEBUG SECTION 1:
        for (int i = 0; i < mSubjectList.size(); i++) {
            String message = mSubjectList.get(i) + " percent : " + mPercentageList.get(i) + " missed : " + mMissedList.get(i) + " out of " + mTotalList.get(i) + "classes.";
            Log.d("Subject" + i, message);
        }
        Log.d("subcode size", mSubjectCodeList.size() + "");
        Log.d("sub size", mSubjectList.size() + "");
        Log.d("percent size", mPercentageList.size() + "");
        Log.d("missed size", mMissedList.size() + "");
        Log.d("total size", mTotalList.size() + "");

        mSubjectCodeList.subList(mSubjectList.size(), mSubjectCodeList.size()).clear();
        Log.d("cleared", "Sub code list unnecessary removed");
        Log.d("subcode size", mSubjectCodeList.size() + "");
        Log.d("sub size", mSubjectList.size() + "");
        Log.d("percent size", mPercentageList.size() + "");
        Log.d("missed size", mMissedList.size() + "");
        Log.d("total size", mTotalList.size() + "");

        for (int i = 0; i < mSubjectCodeList.size(); i++) {
            Log.d("SubCode" + i, mSubjectCodeList.get(i));
        }
        //DEBUG SECTION 1 ENDS

    }

    private void getAbsentTable() {
        Elements tables = parentsCornerDoc.select("table [width=96%]");
        int count = 0;
        int rowNumber;
        int colNumber;
        for (Element table : tables) {
//            if (count == 0) {
//                count++;
//                continue;
//            }
            rowNumber = 0;
            Elements rows = table.select("tr");
            for (Element row : rows) {
                if (rowNumber <= 1) {
                    rowNumber++;
                    continue;
                }
                colNumber = 0;
                AttendanceTableRow AttendanceTableRow = new AttendanceTableRow();
                Elements tds = row.select("td");
                for (Element td : tds) {
                    if (colNumber == 0) {
                        AttendanceTableRow.setDate(td.text());
                    } else {
                        String subject = td.text();
                        String color = td.attr("bgcolor");
                        AttendanceTableRow.addCell(new AttendanceTableCells(subject, color));

                        //Duty Attendence Counter
                        //To increment the duty attendence count for a subject if a yellow color found in table
                        if (color.equalsIgnoreCase("#ff9900") || color.equalsIgnoreCase("#cccc00")) {
                            if (!(subject.equals("SEP") | subject.equals("V") | subject.equals("MENT") | subject.equals("LIB") | subject.equals("U"))) {

                                for (int i = 0; i < mSubjectCodeList.size(); i++) {
                                    if (subject.contains(mSubjectCodeList.get(i))) {
                                        int n = i;
                                        mDutyAttendenceList.set(n, Integer.toString(Integer.parseInt(mDutyAttendenceList.get(n)) + 1));
                                        break;
                                    }
                                }

                            }
                        }
                    }
                    colNumber++;
                }
                rowNumber++;
                tableRows.add(AttendanceTableRow);
            }
        }
    }

    private void populateList(boolean shouldEnableDuty) {
        mRecyclerView.startAnimation(fadeOutAnimation);
        mRecyclerAdapter = new AttendanceAdapter(mSubjectList, mPercentageList, mSubjectCodeList, mTotalList, mMissedList,
                user.getattendanceCutoff(getActivity()), mDutyAttendenceList, shouldEnableDuty);
        mRecyclerView.setAdapter(mRecyclerAdapter);
        mRecyclerView.startAnimation(fadeInAnimation);
    }

    private void saveAttendanceDetails() {
        details = new AttendanceDetails();
        details.setDutyAttendanceList(mDutyAttendenceList);
        details.setMissedList(mMissedList);
        details.setPercentageList(mPercentageList);
        details.setSubjectCodeList(mSubjectCodeList);
        details.setSubjectList(mSubjectList);
        details.setTotalList(mTotalList);
        details.setTableRows(tableRows);

        if (attendanceDAO.getAttendance().size() > 0) {
            attendanceDAO.deleteAll();
            attendanceDAO.insertDetails(details);
        } else {
            attendanceDAO.insertDetails(details);
        }

    }

    @Override
    public void onDestroyView() {
        cancelAsyncTasks();
        super.onDestroyView();
        {

        }
    }

    //For populating spinner
    private class JSoupSpinnerTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onCancelled() {
            super.onCancelled();

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            setDefaultCountValue();
            if (Utils.isNetworkAvailable(getActivity())) {
                mProgressDialog.show();
            } else {
                Utils.doWhenNoNetwork(getActivity());
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            mSpinnerAdapter = new ArrayAdapter<>(view.getContext(),
                    android.R.layout.simple_spinner_item,
                    mSemesters);
            mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(mSpinnerAdapter);
            spinner.setSelection(count);
            mProgressDialog.dismiss();

            //to save users current semesters in case of any changes in the RSMS
            int x = count + 1;
            user.setSem(x);
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences(Constants.PREFERENCE_FILE, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("sem",
                    user.getSem());
            editor.apply();

        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                res = Jsoup.connect(Constants.loginURLStudentsCorner)
                        .data("Userid", user.getUserName())
                        .data("Password", String.valueOf(user.getPassword()))
                        .followRedirects(true)
                        .method(Connection.Method.POST)
                        .execute();

                studentsCorenerDoc = Jsoup.connect(Constants.attendanceURL)
                        .cookies(res.cookies())
                        .get();

                extractSemesterList();

            } catch (IOException e) {
                Log.e(TAG, "Error initialising spinner");
            }
            return null;
        }
    }


    private class JSoupDutyLeaveTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onCancelled() {
            super.onCancelled();

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            setDefaultCountValue();
            if (Utils.isNetworkAvailable(getActivity())) {
                mProgressDialog.show();
            } else {
                Utils.doWhenNoNetwork(getActivity());
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            extractAttendanceData();

            mRecyclerView.setAdapter(mRecyclerAdapter);

            attendanceDetails();
            getAbsentTable();

            saveAttendanceDetails();

            mProgressDialog.dismiss();

            dutyEnablerSelector.check(R.id.attendance_duty_disable);    //to check DISABLE radio button when semester is changed in spinner
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                resParentsCorner = Jsoup.connect(Constants.loginURL)
                        .data("user", user.getUserName())
                        .data("pass", String.valueOf(user.getPassword()))
                        .followRedirects(true)
                        .method(Connection.Method.POST)
                        .execute();

                parentsCornerDoc = Jsoup.connect(Constants.attendanceURLParentsCorner + "?code=" + code)
                        .cookies(res.cookies())
                        .get();

            } catch (IOException e) {
                Log.e(TAG, "Error fetching duty attendance data");
            }
            return null;
        }
    }

    //For populating RecyclerView
    private class JSoupAttendanceTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onCancelled() {
            super.onCancelled();

            if (mProgressDialog.isShowing()) {
                mProgressDialog.hide();
                Utils.doWhenNoNetwork(getActivity());
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (Utils.isNetworkAvailable(getActivity())) {
                mProgressDialog.show();
            } else {
                Utils.doWhenNoNetwork(getActivity());
            }

            initLists();

            //Clear lists before populating recycler view by continuous spinner selections
            clearLists();
            mRecyclerAdapter = new AttendanceAdapter(mSubjectList, mPercentageList, mSubjectCodeList, mTotalList, mMissedList, user.getattendanceCutoff(getActivity()), mDutyAttendenceList, false);
            setDefaultCountValue();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            jSoupDutyLeaveTask = new JSoupDutyLeaveTask();
            jSoupDutyLeaveTask.execute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {

                studentsCorenerDoc = Jsoup.connect(Constants.attendanceURL + "?code=" + code)
                        .cookies(res.cookies())
                        .get();

            } catch (IOException e) {
                Log.e(TAG, "Error retrieving data");
            }

            return null;
        }
    }


}
