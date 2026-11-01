package com.priya.ai
import android.accessibilityservice.AccessibilityService as AS
import android.content.Context
import android.content.Intent
import android.media.*
import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.*

object Actions{
 fun run(c:Context,n:String,a:JSONObject):String{
  if(n=="open_app")return openApp(c,a.optString("name"))
  val s=PriyaService.inst?:return "Accessibility service is OFF"
  return when(n){
   "press"->{s.performGlobalAction(when(a.optString("button").lowercase()){"back"->AS.GLOBAL_ACTION_BACK;"recents"->AS.GLOBAL_ACTION_RECENTS;else->AS.GLOBAL_ACTION_HOME});"done"}
   "click_text"->if(s.clickText(a.optString("text")))"clicked" else "not found"
   "type_text"->if(s.typeText(a.optString("text")))"typed" else "no input field"
   "scroll"->if(s.scroll(a.optString("direction")!="up"))"scrolled" else "cannot scroll"
   else->"unknown tool"
  }
 }
 fun openApp(c:Context,name:String):String{
  val pm=c.packageManager
  val q=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val m=pm.queryIntentActivities(q,0).firstOrNull{it.loadLabel(pm).toString().contains(name,true)}?:return "app not installed"
  val i=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
   .setClassName(m.activityInfo.packageName,m.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  (PriyaService.inst?:c).startActivity(i)
  return "opened "+m.loadLabel(pm)
 }
}

class Live(val ctx:Context,val key:String,val model:String,val voice:String,val on:(String)->Unit){
 var ws:WebSocket?=null; var run=false; var track:AudioTrack?=null; var rec:AudioRecord?=null
 fun fd(n:String,p:String)="""{"name":"$n","description":"$n","parameters":{"type":"OBJECT","properties":{"$p":{"type":"STRING"}},"required":["$p"]}}"""
 fun start(){
  val sys="You are Priya, a warm friendly young woman assistant. Speak in the language the user speaks (Hindi, Bengali or English) and keep replies short. When asked to operate the phone, call the tools."
  val tools=listOf(fd("open_app","name"),fd("press","button"),fd("click_text","text"),fd("type_text","text"),fd("scroll","direction")).joinToString(",")
  val setup="""{"setup":{"model":"models/$model","generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":"$voice"}}}},"systemInstruction":{"parts":[{"text":"$sys"}]},"tools":[{"functionDeclarations":[$tools]}]}}"""
  val bs=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
  track=AudioTrack(AudioManager.STREAM_MUSIC,24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,bs*4,AudioTrack.MODE_STREAM)
  track!!.play()
  val url="wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
  ws=OkHttpClient().newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){
   override fun onOpen(w:WebSocket,r:Response){w.send(setup)}
   override fun onMessage(w:WebSocket,t:String){msg(t)}
   override fun onMessage(w:WebSocket,b:ByteString){msg(b.utf8())}
   override fun onFailure(w:WebSocket,t:Throwable,r:Response?){on("Error: ${t.message} ${r?.code ?: ""}");stop()}
   override fun onClosed(w:WebSocket,c:Int,r:String){on("Closed $c $r")}
  })
 }
 fun msg(s:String){
  try{
   val o=JSONObject(s)
   if(o.has("setupComplete")){on("Listening... bolo!");mic()}
   o.optJSONObject("serverContent")?.let{sc->
    if(sc.optBoolean("interrupted")){track?.pause();track?.flush();track?.play()}
    sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let{ps->
     for(i in 0 until ps.length()){
      val d=ps.getJSONObject(i).optJSONObject("inlineData")?.optString("data")
      if(!d.isNullOrEmpty()){val a=Base64.decode(d,Base64.DEFAULT);track?.write(a,0,a.size)}
     }
    }
   }
   o.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let{fc->
    val rs=JSONArray()
    for(i in 0 until fc.length()){
     val f=fc.getJSONObject(i)
     val r=Actions.run(ctx,f.getString("name"),f.optJSONObject("args")?:JSONObject())
     rs.put(JSONObject().put("id",f.getString("id")).put("name",f.getString("name")).put("response",JSONObject().put("result",r)))
    }
    ws?.send(JSONObject().put("toolResponse",JSONObject().put("functionResponses",rs)).toString())
   }
  }catch(e:Exception){on("Err: ${e.message}")}
 }
 fun mic(){
  if(run)return
  run=true
  val sz=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
  rec=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,sz*2)
  rec!!.startRecording()
  Thread{
   try{
    val buf=ByteArray(3200)
    while(run){
     val n=rec!!.read(buf,0,buf.size)
     if(n>0)ws?.send(JSONObject().put("realtimeInput",JSONObject().put("audio",JSONObject().put("data",Base64.encodeToString(buf,0,n,Base64.NO_WRAP)).put("mimeType","audio/pcm;rate=16000"))).toString())
    }
   }catch(e:Exception){}
  }.start()
 }
 fun say(t:String){
  val p=JSONObject().put("role","user").put("parts",JSONArray().put(JSONObject().put("text",t)))
  ws?.send(JSONObject().put("clientContent",JSONObject().put("turns",JSONArray().put(p)).put("turnComplete",true)).toString())
 }
 fun stop(){
  run=false
  try{rec?.stop();rec?.release();track?.stop();track?.release();ws?.close(1000,null)}catch(e:Exception){}
 }
}
