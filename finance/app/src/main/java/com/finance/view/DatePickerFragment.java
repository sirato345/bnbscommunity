package com.finance.view;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.DatePicker;

import androidx.fragment.app.DialogFragment;

import com.finance.controller.Controller;

import java.util.Calendar;
import java.util.Locale;

public class DatePickerFragment extends DialogFragment
        implements DatePickerDialog.OnDateSetListener {

    private ChartActivity activity;
    private Controller controller;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current date as the default date in the picker
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        activity = (ChartActivity)getActivity();
        controller = activity.getController();
        // Create a new instance of DatePickerDialog and return it
        return new DatePickerDialog(activity, this, year, month, day);
    }

    public void onDateSet(DatePicker view, int year, int month, int day) {
        String monthStr = String.format(Locale.US, "%02d", month + 1);
        String dateStr = String.format(Locale.US, "%02d", day);
        // 選択された日付
        String dateSelect = new StringBuffer().append(year).append(monthStr).append(dateStr).toString();
        int[] offsets = controller.getOffset(activity.getSymbol(), activity.getTimeFrame(), dateSelect);
        int offset = offsets[0];
        activity.setOffset(offset);
        activity.setOffsetBk(offset);
        activity.setOffsetLast(offset);
        activity.showUI();
    }
}