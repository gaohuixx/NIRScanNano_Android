package com.kstechnologies.NanoScan;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.gaohui.utils.ThemeManageUtil;


/**
 * @author lao,gaohui
 */
public class ThemeDialog extends DialogFragment implements View.OnClickListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        final View layout = inflater.inflate(R.layout.dialog_theme, container, false);
        layout.findViewById(R.id.blue_theme).setOnClickListener(this);
        layout.findViewById(R.id.indigo_theme).setOnClickListener(this);
        layout.findViewById(R.id.green_theme).setOnClickListener(this);
        layout.findViewById(R.id.red_theme).setOnClickListener(this);
        layout.findViewById(R.id.blue_grey_theme).setOnClickListener(this);
        layout.findViewById(R.id.black_theme).setOnClickListener(this);
        layout.findViewById(R.id.purple_theme).setOnClickListener(this);
        layout.findViewById(R.id.orange_theme).setOnClickListener(this);
        layout.findViewById(R.id.pink_theme).setOnClickListener(this);
        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    @Override
    public void onClick(View v) {

        int theme;
        switch (v.getId()) {
            case R.id.blue_theme:
                theme = R.style.BlueTheme;
                break;
            case R.id.indigo_theme:
                theme = R.style.IndigoTheme;
                break;
            case R.id.green_theme:
                theme = R.style.GreenTheme;
                break;
            case R.id.red_theme:
                theme = R.style.RedTheme;
                break;
            case R.id.blue_grey_theme:
                theme = R.style.BlueGreyTheme;
                break;
            case R.id.black_theme:
                theme = R.style.BlackTheme;
                break;
            case R.id.orange_theme:
                theme = R.style.OrangeTheme;
                break;
            case R.id.purple_theme:
                theme = R.style.PurpleTheme;
                break;
            case R.id.pink_theme:
                theme = R.style.PinkTheme;
                break;
            default:
                theme = R.style.AppTheme;
                break;
        }
        ThemeManageUtil.currentTheme = theme;
        startActivity(new Intent(getContext(), ScanListActivity.class));
        getActivity().finish();
    }
}
