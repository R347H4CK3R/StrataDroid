package dev.stratadroid.launcher;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  int mode=0;
  LinearLayout root,list;
  TextView label(String value,int size) {
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(14,14,14,14);return t;
  }
  Button button(String name,Runnable action) {
    Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);render();}
  void render(){
    root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(13,17,25));setContentView(root);
    root.addView(label("STRATADROID  •  ANDROID",24));
    HorizontalScrollView bar=new HorizontalScrollView(this);
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(0);
    String[] tabs={"Home","Cinematic","Big Picture","Sources"};
    for(int i=0;i<tabs.length;i++){final int id=i;nav.addView(button(tabs[i],()->{if(id==3)startActivity(new Intent(this,SourcesActivity.class));else{mode=id;render();}}));}
    bar.addView(nav);root.addView(bar);
    root.addView(label("Installed Android apps",16));
    ScrollView sc=new ScrollView(this);root.addView(sc);
    list=new LinearLayout(this);list.setOrientation(1);sc.addView(list);
    Intent intent=new Intent(Intent.ACTION_MAIN);intent.addCategory(Intent.CATEGORY_LAUNCHER);
    PackageManager pm=getPackageManager();
    List<ResolveInfo> apps=new ArrayList<>(pm.queryIntentActivities(intent,0));
    apps.sort(Comparator.comparing(a->a.loadLabel(pm).toString().toLowerCase(Locale.ROOT)));
    if(mode==1){HorizontalScrollView hs=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);row.setOrientation(0);for(ResolveInfo a:apps)row.addView(tile(a,pm,true));hs.addView(row);list.addView(hs);}
    else for(ResolveInfo a:apps)list.addView(tile(a,pm,mode==2));
  }
  View tile(ResolveInfo a,PackageManager pm,boolean large){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(large?1:0);panel.setGravity(Gravity.CENTER_VERTICAL);panel.setPadding(12,10,12,10);panel.setBackgroundColor(Color.rgb(29,35,48));
    ImageView img=new ImageView(this);img.setImageDrawable(a.loadIcon(pm));panel.addView(img,new LinearLayout.LayoutParams(large?80:48,large?80:48));
    panel.addView(label(a.loadLabel(pm).toString(),large?19:16));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(large?230:-1,-2);p.setMargins(3,3,3,3);panel.setLayoutParams(p);
    panel.setOnClickListener(v->{try{Intent launch=pm.getLaunchIntentForPackage(a.activityInfo.packageName);if(launch!=null)startActivity(launch);else Toast.makeText(this,"No launch intent",0).show();}catch(Exception ex){Toast.makeText(this,"Cannot open app",0).show();}});
    return panel;
  }
}
