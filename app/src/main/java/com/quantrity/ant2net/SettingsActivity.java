package com.quantrity.ant2net;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import java.util.Map;
import java.util.regex.Pattern;

import ru.bartwell.exfilepicker.ExFilePicker;
import ru.bartwell.exfilepicker.data.ExFilePickerResult;

public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static Pattern pattern;
    static {
        pattern = Pattern.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        getListView().setDividerHeight(0);

        this.initSummaries(this.getPreferenceScreen());
        this.getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        Preference pref = getPreferenceManager().findPreference("reset");
        if (pref != null) {
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    MainActivity.deleteAllActivities();
                    return true;
                }
            });
        }
        pref = getPreferenceManager().findPreference("forget");
        if (pref != null) {
            Map<String, ?> allEntries = PreferenceManager.getDefaultSharedPreferences(this).getAll();
            boolean found = false;
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                if (entry.getKey().matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
                    found = true;
                }
            }
            if (found) {
                pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        MainActivity.forgetDevices(getApplicationContext());
                        return true;
                    }
                });
            } else {
                pref.setEnabled(false);
            }
        }
        pref = getPreferenceManager().findPreference("backup_folder");
        if (pref != null) {
            pref.setOnPreferenceClickListener(new EditTextPreference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ExFilePicker exFilePicker = new ExFilePicker();
                    exFilePicker.setCanChooseOnlyOneItem(true);
                    exFilePicker.setSortButtonDisabled(true);
                    exFilePicker.setChoiceType(ExFilePicker.ChoiceType.DIRECTORIES);
                    exFilePicker.setQuitButtonEnabled(true);
                    String backup_folder = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("backup_folder", "");
                    if (!backup_folder.equals("")) exFilePicker.setStartDirectory(backup_folder);
                    exFilePicker.start(SettingsActivity.this, MainActivity.EX_DIRECTORY_PICKER_RESULT);
                    return true;
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getListView().setDividerHeight(0);

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Set summary to be the user-description for the selected value
        Preference pref = findPreference(key);
        if ((key == null) || (pref == null)) return;
        if (key.equals("gmail_to")) {
            String text = ((EditTextPreference)pref).getText();
            if (text != null) {
                text = text.trim();
                if ((text.length() > 0) && (!pattern.matcher(text).matches()))
                    showMessage(getString(R.string.preferences_invalid_email));
            }
        }
        this.setSummary(pref);
    }

    void showMessage(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                }).create().show();
    }

    /* Set the summaries of all preferences */
    private void initSummaries(PreferenceGroup pg) {
        for (int i = 0; i < pg.getPreferenceCount(); ++i) {
            Preference p = pg.getPreference(i);
            if (p instanceof PreferenceGroup) this.initSummaries((PreferenceGroup) p); // recursion
            else this.setSummary(p);
        }
    }

    /* Set the summaries of the given preference */
    private void setSummary(Preference pref) {
        if (pref instanceof EditTextPreference) {
            EditTextPreference sPref = (EditTextPreference) pref;
            if (sPref.getText() != null) {
                if (sPref.getText().length() == 0) {
                    sPref.setText(null);
                    pref.setSummary("");
                } else {
                    if (sPref.getKey().contains("pass")) {
                        StringBuilder masked_pwd = new StringBuilder();
                        for (int i = 0; i < sPref.getText().length(); i++) masked_pwd.append("*");
                        pref.setSummary(masked_pwd.toString());
                    } else pref.setSummary(sPref.getText());
                }
            }
        } else if (pref.getKey().equals("backup_folder"))
        {
            String backup_folder = PreferenceManager.getDefaultSharedPreferences(this).getString("backup_folder", "");
            if (!backup_folder.equals("")) pref.setSummary(backup_folder);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == MainActivity.EX_DIRECTORY_PICKER_RESULT) {
            ExFilePickerResult result = ExFilePickerResult.getFromIntent(data);
            if ((result != null) && (result.getCount() > 0)) {
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("backup_folder", result.getPath() + result.getNames().get(0)).apply();
                this.onSharedPreferenceChanged(getPreferenceScreen().getSharedPreferences(), "backup_folder");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
