package com.djiman.bugobi.advance34.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.djiman.bugobi.advance34.DjController;
import com.djiman.bugobi.advance34.model.TrackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class LibraryDialog {
    private LibraryDialog() {}

    public static void show(Context context, DjController dj, String title, Consumer<TrackInfo> selected) {
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,12,18,12);root.setBackgroundColor(Color.rgb(14,17,20));
        EditText search=new EditText(context);search.setHint("Search music");search.setTextColor(Color.WHITE);search.setHintTextColor(Color.GRAY);root.addView(search,new LinearLayout.LayoutParams(-1,-2));
        ListView list=new ListView(context);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView status=new TextView(context);status.setText("Reading MediaStore…");status.setTextColor(Color.LTGRAY);status.setPadding(8,8,8,8);root.addView(status);
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle(title).setView(root).setNegativeButton("CANCEL",null).create();
        ArrayList<TrackInfo> all=new ArrayList<>(),visible=new ArrayList<>();
        ArrayAdapter<TrackInfo> adapter=new ArrayAdapter<>(context,android.R.layout.simple_list_item_2,android.R.id.text1,visible){
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent){View v=super.getView(position,convertView,parent);TrackInfo t=getItem(position);TextView a=v.findViewById(android.R.id.text1),b=v.findViewById(android.R.id.text2);a.setText(t.title);a.setTextColor(Color.WHITE);b.setText(t.artist+"  •  "+formatMs(t.durationMs));b.setTextColor(Color.LTGRAY);v.setBackgroundColor(Color.rgb(20,24,28));return v;}
        };list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->{TrackInfo t=visible.get(pos);dialog.dismiss();selected.accept(t);});
        Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(TrackInfo t:all)if(q.isEmpty()||(t.title+" "+t.artist+" "+t.album).toLowerCase(Locale.ROOT).contains(q))visible.add(t);adapter.notifyDataSetChanged();status.setText(visible.size()+" tracks");};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});
        dialog.setOnShowListener(x->{ExecutorService ex=Executors.newSingleThreadExecutor();ex.execute(()->{List<TrackInfo> tracks=dj.queryLibrary();root.post(()->{all.clear();all.addAll(tracks);filter.run();});ex.shutdown();});});
        dialog.show();
    }

    private static String formatMs(long ms){long s=Math.max(0,ms/1000),m=s/60;return String.format(Locale.US,"%d:%02d",m,s%60);}
}
