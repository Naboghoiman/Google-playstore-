package com.djiman.bugobi.advance34.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.djiman.bugobi.advance34.DjController;
import com.djiman.bugobi.advance34.automix.AutomixController;
import com.djiman.bugobi.advance34.model.TrackInfo;

import java.util.ArrayList;
import java.util.Locale;

public final class AutomixDialog {
    private AutomixDialog() {}

    public static void show(Context c, DjController dj, AutomixController automix) {
        LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,8,18,8);root.setBackgroundColor(Color.rgb(14,17,20));
        TextView hint=new TextView(c);hint.setText("AUTOMIX QUEUE • tap ADD SONG • long-press a queued song to remove");hint.setTextColor(Color.LTGRAY);hint.setPadding(4,8,4,8);root.addView(hint);
        ListView list=new ListView(c);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(c);buttons.setOrientation(LinearLayout.HORIZONTAL);root.addView(buttons);
        Button add=new Button(c);add.setText("ADD SONG");buttons.addView(add,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=new Button(c);settings.setText("SETTINGS");buttons.addView(settings,new LinearLayout.LayoutParams(0,-2,1));
        Button start=new Button(c);start.setText(automix.isRunning()?"STOP":"START");buttons.addView(start,new LinearLayout.LayoutParams(0,-2,1));
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle("DJ IMAN AUTOMIX").setView(root).setNegativeButton("CLOSE",null).create();
        ArrayList<TrackInfo> rows=new ArrayList<>();
        ArrayAdapter<TrackInfo> adapter=new ArrayAdapter<>(c,android.R.layout.simple_list_item_2,android.R.id.text1,rows){@Override public android.view.View getView(int p,android.view.View v,android.view.ViewGroup g){v=super.getView(p,v,g);TrackInfo t=getItem(p);TextView a=v.findViewById(android.R.id.text1),b=v.findViewById(android.R.id.text2);a.setText((p+1)+". "+t.title);a.setTextColor(Color.WHITE);b.setText(t.artist);b.setTextColor(Color.LTGRAY);v.setBackgroundColor(Color.rgb(20,24,28));return v;}};list.setAdapter(adapter);
        Runnable refresh=()->{rows.clear();rows.addAll(automix.queueSnapshot());adapter.notifyDataSetChanged();start.setText(automix.isRunning()?"STOP":"START");};refresh.run();
        list.setOnItemLongClickListener((p,v,pos,id)->{automix.remove(rows.get(pos));refresh.run();return true;});
        add.setOnClickListener(v->LibraryDialog.show(c,dj,"Add to Automix",t->{automix.add(t);refresh.run();}));
        settings.setOnClickListener(v->showSettings(c,automix));
        start.setOnClickListener(v->{if(automix.isRunning())automix.stop();else automix.start();refresh.run();});
        dialog.show();
    }

    private static void showSettings(Context c,AutomixController a){
        LinearLayout r=new LinearLayout(c);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(24,8,24,8);
        EditText play=num(c,"Minutes per song",a.settings.playMinutes);r.addView(play);
        Spinner mode=new Spinner(c);mode.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,new String[]{"Start by seconds","Start by percent"}));mode.setSelection(a.settings.startMode==AutomixController.StartMode.SECONDS?0:1);r.addView(mode);
        EditText sec=num(c,"Starting seconds",a.settings.startSeconds);r.addView(sec);EditText pct=num(c,"Starting percent",a.settings.startPercent);r.addView(pct);EditText trans=num(c,"Transition seconds",a.settings.transitionSeconds);r.addView(trans);
        CheckBox remove=check(c,"Remove played song from queue",a.settings.removePlayed);r.addView(remove);CheckBox preload=check(c,"Preload/analyse next song",a.settings.preloadNext);r.addView(preload);CheckBox sampler=check(c,"Sampler hit during transition",a.settings.samplerEffects);r.addView(sampler);CheckBox lock=check(c,"Keep incoming SYNC locked",a.settings.syncLockIncoming);r.addView(lock);
        new AlertDialog.Builder(c).setTitle("Automix settings").setView(r).setPositiveButton("SAVE",(d,w)->{a.settings.playMinutes=read(play,2.5);a.settings.startMode=mode.getSelectedItemPosition()==0?AutomixController.StartMode.SECONDS:AutomixController.StartMode.PERCENT;a.settings.startSeconds=read(sec,8);a.settings.startPercent=read(pct,5);a.settings.transitionSeconds=Math.max(2,read(trans,12));a.settings.removePlayed=remove.isChecked();a.settings.preloadNext=preload.isChecked();a.settings.samplerEffects=sampler.isChecked();a.settings.syncLockIncoming=lock.isChecked();a.saveSettings();}).setNegativeButton("CANCEL",null).show();
    }
    private static EditText num(Context c,String hint,double v){EditText e=new EditText(c);e.setHint(hint);e.setText(String.format(Locale.US,"%.1f",v));e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private static CheckBox check(Context c,String text,boolean v){CheckBox x=new CheckBox(c);x.setText(text);x.setChecked(v);return x;}
    private static double read(EditText e,double fallback){try{return Double.parseDouble(e.getText().toString());}catch(Exception x){return fallback;}}
}
