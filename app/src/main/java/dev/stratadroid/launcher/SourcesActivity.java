package dev.stratadroid.launcher;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class SourcesActivity extends Activity {
  static final long LIMIT=1024L*1024*1024;
  LinearLayout list;
  Handler handler=new Handler(Looper.getMainLooper());
  Map<String,LinearLayout> panels=new HashMap<>();
  File pending;
  boolean alive=true;
  TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setPadding(10,10,10,10);return t;}
  Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
  LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);return c;}
  @Override public void onCreate(Bundle b){super.onCreate(b);render();}
  @Override public void onDestroy(){alive=false;super.onDestroy();}
  @Override public void onResume(){super.onResume();if(pending!=null && getPackageManager().canRequestPackageInstalls()){File f=pending;pending=null;install(f);}}
  SortedSet<String> repos(){return new TreeSet<>(getSharedPreferences("sources",0).getStringSet("repos",Collections.emptySet()));}
  void save(Set<String> set){getSharedPreferences("sources",0).edit().putStringSet("repos",new HashSet<>(set)).apply();}
  static String normalize(String s){
    s=s.trim();if(s.startsWith("https://github.com/"))s=s.substring(19);
    if(s.endsWith("/"))s=s.substring(0,s.length()-1);
    return s.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")&&!s.contains("..")?s:null;
  }
  void render(){
    panels.clear();ScrollView scroll=new ScrollView(this);LinearLayout root=column();root.setPadding(12,12,12,12);root.setBackgroundColor(Color.rgb(13,17,25));scroll.addView(root);setContentView(scroll);
    root.addView(label("STRATA / SOURCES"));
    root.addView(label("Android APK releases are installable; iOS IPA and source-only releases are not."));
    root.addView(button("+ Add GitHub source",()->{
      EditText field=new EditText(this);field.setSingleLine(true);field.setHint("owner/repo or GitHub URL");
      new AlertDialog.Builder(this).setTitle("Add source").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Add",(d,w)->{
        String repo=normalize(field.getText().toString());
        if(repo==null){toast("Invalid GitHub repository");return;}
        SortedSet<String> all=repos();all.add(repo);save(all);render();check(repo);
      }).show();
    }));
    list=column();root.addView(list);
    for(String repo:repos()){
      LinearLayout card=column();card.setPadding(8,8,8,8);card.setBackgroundColor(Color.rgb(29,35,48));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,8,0,8);list.addView(card,lp);
      card.addView(label(repo));card.addView(button("Check latest release",()->check(repo)));
      card.addView(button("Remove source",()->{SortedSet<String> all=repos();all.remove(repo);save(all);render();}));
      LinearLayout content=column();content.addView(label("Not checked"));card.addView(content);panels.put(repo,content);
    }
  }
  void display(String repo,String s){LinearLayout p=panels.get(repo);if(p!=null){p.removeAllViews();p.addView(label(s));}}
  void check(String repo){
    display(repo,"Checking GitHub release...");
    new Thread(()->{
      try{
        URL url=new URL("https://api.github.com/repos/"+repo+"/releases/latest");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);
        c.setRequestProperty("Accept","application/vnd.github+json");
        if(c.getResponseCode()!=200)throw new IOException("GitHub HTTP "+c.getResponseCode()+" (no release, private repository or rate limit)");
        String data;
        try(InputStream in=c.getInputStream()){data=read(in,2000000);}
        finally{c.disconnect();}
        JSONObject result=new JSONObject(data);JSONArray assets=result.optJSONArray("assets");
        ArrayList<Asset> apks=new ArrayList<>();ArrayList<String> other=new ArrayList<>();
        if(assets!=null)for(int i=0;i<assets.length();i++){
          JSONObject a=assets.getJSONObject(i);String name=a.optString("name");String urlString=a.optString("browser_download_url");
          if(name.toLowerCase(Locale.ROOT).endsWith(".apk")&&validUrl(urlString))
            apks.add(new Asset(name,urlString,a.optLong("size",-1)));
          else other.add(name);
        }
        String version=result.optString("tag_name","latest");
        handler.post(()->{if(!alive||!repos().contains(repo))return;
          LinearLayout p=panels.get(repo);if(p==null)return;p.removeAllViews();
          p.addView(label("Release: "+version));
          if(apks.isEmpty())p.addView(label("SOURCE ONLY / NO ANDROID APK"));
          else{p.addView(label("ANDROID APK AVAILABLE ("+apks.size()+")"));for(Asset a:apks)p.addView(button("Download and install "+a.name,()->confirm(repo,a)));}
          if(!other.isEmpty())p.addView(label("Other assets (not Android APK): "+String.join(", ",other)));
        });
      }catch(Exception ex){handler.post(()->{if(alive)display(repo,"Release check failed: "+ex.getMessage());});}
    }).start();
  }
  static class Asset{String name,url;long size;Asset(String n,String u,long s){name=n;url=u;size=s;}}
  static boolean validUrl(String s){try{URI u=new URI(s);return "https".equals(u.getScheme())&&"github.com".equals(u.getHost())&&u.getPort()==-1&&u.getRawQuery()==null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
  static boolean trusted(URL u){String h=u.getHost().toLowerCase(Locale.ROOT);return u.getProtocol().equals("https")&&u.getPort()==-1&&u.getUserInfo()==null&&(h.equals("github.com")||h.equals("release-assets.githubusercontent.com")||h.equals("objects.githubusercontent.com"));}
  void confirm(String repo,Asset a){
    new AlertDialog.Builder(this).setTitle("Install Android APK?").setMessage(repo+"\n"+a.name+"\nDownload size: "+a.size+" bytes\nOnly install APKs from trusted developers. StrataDroid cannot verify the APK's safety or signature.")
      .setNegativeButton("Cancel",null).setPositiveButton("Download", (d,w)->download(a)).show();
  }
  void download(Asset a){
    if(a.size<=0||a.size>LIMIT){toast("Invalid APK size");return;}
    toast("Downloading "+a.name);
    new Thread(()->{File file=null;
      try{
        File dir=new File(getCacheDir(),"apks");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cache directory");
        file=File.createTempFile("release-",".apk",dir);
        URL current=new URL(a.url);HttpURLConnection c=null;
        for(int i=0;i<6;i++){
          if(!trusted(current))throw new IOException("Untrusted download address");
          c=(HttpURLConnection)current.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
          int status=c.getResponseCode();
          if(status>=300&&status<=308){String location=c.getHeaderField("Location");c.disconnect();c=null;if(location==null)throw new IOException("Invalid redirect");current=new URL(current,location);continue;}
          if(status!=200){c.disconnect();throw new IOException("Download HTTP "+status);}break;
        }
        if(c==null)throw new IOException("Excess redirects");
        long count=0;
        try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
          byte[] buffer=new byte[65536];int n;
          while((n=in.read(buffer))!=-1){count+=n;if(count>LIMIT)throw new IOException("APK too large");out.write(buffer,0,n);}
        }finally{c.disconnect();}
        if(count!=a.size)throw new IOException("Incorrect download length");
        try(InputStream in=new FileInputStream(file)){if(in.read()!=80||in.read()!=75)throw new IOException("Invalid APK format");}
        File f=file;handler.post(()->{if(alive)askInstall(f);});
      }catch(Exception e){if(file!=null)file.delete();handler.post(()->{if(alive)toast("Download failed: "+e.getMessage());});}
    }).start();
  }
  void askInstall(File f){
    if(!getPackageManager().canRequestPackageInstalls()){
      pending=f;
      new AlertDialog.Builder(this).setTitle("Allow APK installation").setMessage("Enable installation permission for StrataDroid in Android settings, then return.")
        .setNegativeButton("Cancel",(d,w)->pending=null)
        .setPositiveButton("Settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())))).show();
    }else install(f);
  }
  void install(File f){
    try{
      Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
      Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(i);
    }catch(Exception ex){toast("Installer failed: "+ex.getMessage());}
  }
  static String read(InputStream in,int max)throws IOException{
    ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;
    while((n=in.read(bytes))!=-1){if(b.size()+n>max)throw new IOException("API response too large");b.write(bytes,0,n);}
    return b.toString("UTF-8");
  }
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
