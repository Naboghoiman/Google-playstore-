package com.djiman.bugobi.advance34.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.djiman.bugobi.advance34.DjController;
import com.djiman.bugobi.advance34.audio.NativeDjEngine;
import com.djiman.bugobi.advance34.audio.SyncController;

public final class SettingsDialog {
    private SettingsDialog() {}
    public static void show(Context c, DjController dj) {
        LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,16,28,16);
        TextView s1=new TextView(c);s1.setText("SYNC MODE");root.addView(s1);
        Spinner sync=new Spinner(c);sync.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tempo + beat","Tempo only","Bar / phrase"}));
        sync.setSelection(dj.sync.mode()==SyncController.Mode.TEMPO?1:dj.sync.mode()==SyncController.Mode.BAR?2:0);root.addView(sync);
        TextView s2=new TextView(c);s2.setText("CROSSFADER CURVE");root.addView(s2);
        Spinner curve=new Spinner(c);curve.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,new String[]{"Smooth","Linear","Scratch"}));root.addView(curve);
        CheckBox auto=new CheckBox(c);auto.setText("Automatic channel trim match");auto.setChecked(dj.isAutoTrimEnabled());root.addView(auto);
        new AlertDialog.Builder(c).setTitle("DJ IMAN SETTINGS").setView(root).setPositiveButton("APPLY",(d,w)->{
            int m=sync.getSelectedItemPosition();dj.sync.setMode(m==1?SyncController.Mode.TEMPO:m==2?SyncController.Mode.BAR:SyncController.Mode.BEAT);
            int x=curve.getSelectedItemPosition();dj.engine.setCrossfaderCurve(x==0?NativeDjEngine.CROSSFADER_SMOOTH:x==1?NativeDjEngine.CROSSFADER_LINEAR:NativeDjEngine.CROSSFADER_SCRATCH);
            dj.setAutoTrimEnabled(auto.isChecked());
        }).setNegativeButton("CANCEL",null).show();
    }
}
