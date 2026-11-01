package com.priya.ai
import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo as N

class PriyaService:AccessibilityService(){
 companion object{var inst:PriyaService?=null}
 override fun onServiceConnected(){inst=this}
 override fun onAccessibilityEvent(e:AccessibilityEvent?){}
 override fun onInterrupt(){}
 override fun onDestroy(){inst=null;super.onDestroy()}
 fun clickText(t:String):Boolean{
  val r=rootInActiveWindow?:return false
  for(x in r.findAccessibilityNodeInfosByText(t)){
   var c:N?=x
   while(c!=null){ if(c.isClickable){c.performAction(N.ACTION_CLICK);return true}; c=c.parent }
  }
  return false
 }
 fun typeText(t:String):Boolean{
  val f=findFocus(N.FOCUS_INPUT)?:return false
  val b=Bundle(); b.putCharSequence(N.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,t)
  return f.performAction(N.ACTION_SET_TEXT,b)
 }
 fun scrollable(n:N?):N?{
  if(n==null)return null
  if(n.isScrollable)return n
  for(i in 0 until n.childCount){scrollable(n.getChild(i))?.let{return it}}
  return null
 }
 fun scroll(down:Boolean)=scrollable(rootInActiveWindow)?.performAction(if(down)N.ACTION_SCROLL_FORWARD else N.ACTION_SCROLL_BACKWARD)==true
}
