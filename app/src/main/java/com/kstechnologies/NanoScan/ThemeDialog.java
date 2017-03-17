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
        layout.findViewById(R.id.color1).setOnClickListener(this);
        layout.findViewById(R.id.color2).setOnClickListener(this);
        layout.findViewById(R.id.color3).setOnClickListener(this);
        layout.findViewById(R.id.color4).setOnClickListener(this);
        layout.findViewById(R.id.color5).setOnClickListener(this);
        layout.findViewById(R.id.color6).setOnClickListener(this);
        layout.findViewById(R.id.color7).setOnClickListener(this);
        layout.findViewById(R.id.color10).setOnClickListener(this);
        layout.findViewById(R.id.color11).setOnClickListener(this);
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
            case R.id.color1:
                theme = R.style.Color1;
                break;
            case R.id.color2:
                theme = R.style.Color2;
                break;
            case R.id.color3:
                theme = R.style.Color3;
                break;
            case R.id.color4:
                theme = R.style.Color4;
                break;
            case R.id.color5:
                theme = R.style.Color5;
                break;
            case R.id.color6:
                theme = R.style.Color6;
                break;
            case R.id.color7:
                theme = R.style.Color7;
                break;
            case R.id.color10:
                theme = R.style.Color10;
                break;
            case R.id.color11:
                theme = R.style.Color11;
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
