package com.priya.ai
import android.Manifest.permission as M
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*

class MainActivity:Activity(){
 lateinit var root:FrameLayout
 lateinit var sp:SharedPreferences
 var live:Live?=null
 var st="Tap mic to talk"
 var statusV:TextView?=null

 // 3-layer menu: layer1 -> layer2 -> layer3 (leaf = command sent to Priya). Edit freely.
 val menu:Map<String,Any> = linkedMapOf(
  "Apps" to linkedMapOf(
   "Social" to linkedMapOf("WhatsApp" to "Open WhatsApp","Facebook" to "Open Facebook","Instagram" to "Open Instagram","TikTok" to "Open TikTok","Imo" to "Open imo"),
   "Media" to linkedMapOf("YouTube" to "Open YouTube","Camera" to "Open Camera","Gallery" to "Open Gallery","Spotify" to "Open Spotify"),
   "Tools" to linkedMapOf("Settings" to "Open Settings","Chrome" to "Open Chrome","Calculator" to "Open Calculator","Clock" to "Open Clock")),
  "Phone control" to linkedMapOf(
   "Navigate" to linkedMapOf("Home" to "Press home button","Back" to "Press back button","Recents" to "Open recent apps"),
   "Screen" to linkedMapOf("Scroll down" to "Scroll down","Scroll up" to "Scroll up"),
   "Communication" to linkedMapOf("Phone" to "Open Phone dialer","Contacts" to "Open Contacts","Messages" to "Open Messages")),
  "Priya" to linkedMapOf(
   "Chat" to linkedMapOf("Say hello" to "Say hello to me","Tell a joke" to "Tell me a short joke"),
   "Info" to linkedMapOf("Time" to "What time is it?","Weather" to "How is the weather today?"),
   "Help" to linkedMapOf("What can you do?" to "What can you do for me?"))
 )

 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  sp=getSharedPreferences("p",0)
  root=FrameLayout(this); root.setBackgroundColor(0xFF0B0B1A.toInt())
  setContentView(root)
  splash()
 }
 fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 fun col():LinearLayout{val l=LinearLayout(this);l.orientation=LinearLayout.VERTICAL;l.gravity=Gravity.CENTER_HORIZONTAL;l.setPadding(dp(20),dp(40),dp(20),dp(20));return l}
 fun show(v:View){root.removeAllViews();val s=ScrollView(this);s.addView(v);root.addView(s)}
 fun tv(t:String,s:Float=16f)=TextView(this).apply{text=t;textSize=s;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(8))}
 fun btn(t:String,f:()->Unit)=Button(this).apply{text=t;isAllCaps=false;setOnClickListener{f()}}
 fun img(sz:Int)=ImageView(this).apply{
  setImageResource(R.drawable.priya);scaleType=ImageView.ScaleType.CENTER_CROP
  layoutParams=LinearLayout.LayoutParams(dp(sz),dp(sz));clipToOutline=true
  outlineProvider=object:ViewOutlineProvider(){override fun getOutline(v:View,o:Outline){o.setOval(0,0,v.width,v.height)}}
 }
 fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()

 fun splash(){
  val c=col();c.addView(img(180));c.addView(tv("Priya AI",30f));c.addView(tv("Loading..."));show(c)
  Handler(Looper.getMainLooper()).postDelayed({home()},2000)
 }
 fun home(){
  val c=col();c.addView(img(240));c.addView(tv("Priya AI",26f))
  statusV=tv(st);c.addView(statusV)
  c.addView(btn(if(live==null)"🎤 Start talking" else "⏹ Stop"){toggle()})
  c.addView(btn("☰ Menu"){showMenu("Menu",menu){home()}})
  c.addView(btn("🔐 Permissions"){perms()})
  c.addView(btn("🔑 API"){api()})
  show(c)
 }
 fun toggle(){
  if(live!=null){live!!.stop();live=null;st="Stopped";home();return}
  val key=sp.getString("key","")!!
  if(key.isBlank()){toast("Pehle API key save karo");api();return}
  if(checkSelfPermission(M.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(M.RECORD_AUDIO),2);return}
  live=Live(this,key,sp.getString("model",DM)!!,sp.getString("voice","Aoede")!!){s->runOnUiThread{st=s;statusV?.text=s}}
  live!!.start();st="Connecting...";home()
 }
 val DM="gemini-2.5-flash-native-audio-preview-09-2025"

 @Suppress("UNCHECKED_CAST")
 fun showMenu(title:String,node:Map<String,Any>,back:()->Unit){
  val c=col();c.addView(tv(title,22f))
  for((k,v) in node)c.addView(btn(k){
   if(v is Map<*,*>)showMenu(k,v as Map<String,Any>){showMenu(title,node,back)} else cmd(v as String)
  })
  c.addView(btn("← Back"){back()});show(c)
 }
 fun cmd(t:String){
  if(live==null){toast("Pehle 'Start talking' dabao");return}
  live!!.say(t);toast("Sent: $t")
 }

 fun perms(){
  val c=col();c.addView(tv("Permissions",22f))
  val rt=listOf("Microphone" to M.RECORD_AUDIO,"Notifications" to M.POST_NOTIFICATIONS,"Contacts" to M.READ_CONTACTS,
   "Phone calls" to M.CALL_PHONE,"Phone state" to M.READ_PHONE_STATE,"Camera" to M.CAMERA)
  for((n,p) in rt){
   val ok=checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED
   c.addView(btn((if(ok)"✅ " else "❌ ")+n){requestPermissions(arrayOf(p),1)})
  }
  c.addView(btn("♿ Accessibility (phone control)"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
  c.addView(btn("🪟 Display over other apps"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))})
  c.addView(btn("🔋 Battery optimization"){startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))})
  c.addView(btn("⚙ App settings"){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))})
  c.addView(btn("Allow all runtime permissions"){requestPermissions(rt.map{it.second}.toTypedArray(),1)})
  c.addView(btn("← Back"){home()});show(c)
 }
 override fun onRequestPermissionsResult(code:Int,p:Array<out String>,r:IntArray){
  if(code==2)toggle() else perms()
 }

 fun api(){
  val c=col();c.addView(tv("Gemini API",22f))
  fun ed(h:String,k:String,d:String,pw:Boolean=false)=EditText(this).apply{
   hint=h;setText(sp.getString(k,d));setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)
   inputType=InputType.TYPE_CLASS_TEXT or(if(pw)InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
  }
  val k=ed("Gemini API key","key","",true);val m=ed("Live model name","model",DM);val v=ed("Voice (Aoede, Kore, Puck...)","voice","Aoede")
  c.addView(k);c.addView(m);c.addView(v)
  c.addView(btn("💾 Save"){
   sp.edit().putString("key",k.text.toString().trim()).putString("model",m.text.toString().trim()).putString("voice",v.text.toString().trim()).apply()
   toast("Saved");home()
  })
  c.addView(btn("← Back"){home()});show(c)
 }
}
