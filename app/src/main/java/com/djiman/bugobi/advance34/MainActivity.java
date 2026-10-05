package com.djiman.bugobi.advance34;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.djiman.bugobi.advance34.automix.AutomixController;
import com.djiman.bugobi.advance34.model.TrackInfo;
import com.djiman.bugobi.advance34.ui.AutomixDialog;
import com.djiman.bugobi.advance34.ui.DjMainView;
import com.djiman.bugobi.advance34.ui.LibraryDialog;
import com.djiman.bugobi.advance34.ui.SettingsDialog;
import com.djiman.bugobi.advance34.util.AccessManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements DjMainView.Host {
    private static final int AUDIO_PERMISSION=44;
    private AccessManager access;
    private DjController dj;
    private AutomixController automix;
    private DjMainView view;
    private final ExecutorService accessWorker=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);immersive();access=new AccessManager(this);if(access.restored())requestAudioAndOpen();else showAccessGate();}

    private void immersive(){
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showAccessGate(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(42,120,42,40);root.setBackgroundColor(Color.rgb(6,9,12));
        TextView title=new TextView(this);title.setText("DJ IMAN");title.setTextColor(Color.WHITE);title.setTextSize(44);title.setGravity(Gravity.CENTER);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=new TextView(this);sub.setText("PRIVATE PERFORMANCE BUILD\nEnter your opening code to unlock the mixer.");sub.setTextColor(Color.LTGRAY);sub.setTextSize(16);sub.setGravity(Gravity.CENTER);sub.setPadding(0,18,0,28);root.addView(sub);
        EditText code=new EditText(this);code.setHint("OPENING CODE");code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);code.setTextColor(Color.WHITE);code.setHintTextColor(Color.GRAY);root.addView(code,new LinearLayout.LayoutParams(-1,-2));
        CheckBox remember=new CheckBox(this);remember.setText("Remember access on this device");remember.setTextColor(Color.LTGRAY);root.addView(remember);
        Button open=new Button(this);open.setText("UNLOCK DJ IMAN");root.addView(open,new LinearLayout.LayoutParams(-1,-2));
        TextView status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setGravity(Gravity.CENTER);status.setPadding(0,16,0,0);root.addView(status);
        Runnable updateBlock=()->{int seconds=access.remainingSeconds();if(seconds>0){open.setEnabled(false);open.setText("TRY AGAIN IN "+seconds+"s");status.setText("Too many attempts. Please wait.");root.postDelayed(this::showAccessGate,1000);}else open.setEnabled(true);};
        open.setOnClickListener(v->{int remain=access.remainingSeconds();if(remain>0){updateBlock.run();return;}open.setEnabled(false);open.setText("CHECKING CODE…");status.setText("Checking your opening code…");String entered=code.getText().toString();boolean keep=remember.isChecked();accessWorker.execute(()->{try{access.redeem(entered,keep);runOnUiThread(this::requestAudioAndOpen);}catch(Exception e){runOnUiThread(()->{status.setTextColor(Color.rgb(255,95,95));status.setText(e.getMessage());open.setText("UNLOCK DJ IMAN");open.setEnabled(access.remainingSeconds()==0);if(access.remainingSeconds()>0)updateBlock.run();});}});});
        setContentView(root);updateBlock.run();
    }

    private void requestAudioAndOpen(){
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{permission},AUDIO_PERMISSION);}else openMixer();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==AUDIO_PERMISSION)openMixer();}

    private void openMixer(){if(dj!=null)return;try{dj=new DjController(this);automix=new AutomixController(this,dj);view=new DjMainView(this,dj,automix,this);setContentView(view);immersive();}catch(Throwable e){new AlertDialog.Builder(this).setTitle("Audio engine could not start").setMessage(e.toString()).setPositiveButton("CLOSE",null).show();}}

    @Override public void openLibraryForDeck(int deck){LibraryDialog.show(this,dj,deck==0?"Load Deck A":"Load Deck B",track->dj.loadTrack(deck,track));}
    @Override public void openLibraryGeneral(){LibraryDialog.show(this,dj,"Music Library",track->new AlertDialog.Builder(this).setTitle(track.title).setItems(new String[]{"LOAD TO DECK A","LOAD TO DECK B","ADD TO AUTOMIX"},(d,w)->{if(w==0)dj.loadTrack(0,track);else if(w==1)dj.loadTrack(1,track);else automix.add(track);}).show());}
    @Override public void openLooperLoader(int slot){LibraryDialog.show(this,dj,"Load Loop "+(slot+1),track->{Toast.makeText(this,"Analysing loop…",Toast.LENGTH_SHORT).show();dj.loadLooper(slot,track,()->Toast.makeText(this,"Loop "+(slot+1)+" ready — SYNC TO MASTER",Toast.LENGTH_SHORT).show(),e->Toast.makeText(this,"Loop load failed: "+e.getMessage(),Toast.LENGTH_LONG).show());});}
    @Override public void openSampleLoader(int slot){LibraryDialog.show(this,dj,"Load Sampler Pad "+(slot+1),track->{Toast.makeText(this,"Loading sample…",Toast.LENGTH_SHORT).show();dj.loadSample(slot,track,()->Toast.makeText(this,"Sampler pad ready",Toast.LENGTH_SHORT).show(),e->Toast.makeText(this,"Sample load failed: "+e.getMessage(),Toast.LENGTH_LONG).show());});}
    @Override public void openAutomix(){AutomixDialog.show(this,dj,automix);}
    @Override public void openSettings(){SettingsDialog.show(this,dj);}

    @Override public void onBackPressed(){if(view!=null&&view.goBack())return;super.onBackPressed();}
    @Override protected void onResume(){super.onResume();immersive();}
    @Override protected void onDestroy(){if(automix!=null)automix.close();if(dj!=null)dj.close();accessWorker.shutdownNow();super.onDestroy();}
}
