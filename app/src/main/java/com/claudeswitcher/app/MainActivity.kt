package com.claudeswitcher.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ProfileStore
import java.util.UUID
import org.json.JSONObject

class MainActivity : Activity() {
    private val home = "https://claude.ai/"
    private val bg = Color.rgb(26,22,38)
    private val panel = Color.rgb(37,30,55)
    private val accent = Color.rgb(255,155,101)
    private val palette = intArrayOf(0xffa64e25.toInt(),0xff6551a8.toInt(),0xff24689d.toInt(),0xff39795f.toInt(),0xff994d77.toInt())
    private lateinit var rail: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var stage: FrameLayout
    private lateinit var title: TextView
    private lateinit var progress: ProgressBar
    private lateinit var prefs: android.content.SharedPreferences
    private var state = JSONObject("{\"accounts\":[],\"selected\":null}")
    private val pages = linkedMapOf<String,WebView>()
    private val loading = mutableMapOf<String,Int>()
    // Lazy, per-profile recovery only if Android actually destroys this Activity.
    private var savedPages = Bundle()
    private var picker: android.webkit.ValueCallback<Array<Uri>>? = null
    private var pickerOwner: String? = null
    private fun dp(n:Int) = (n*resources.displayMetrics.density+0.5f).toInt()
    private fun selected():String? = state.optString("selected","").takeUnless{it.isBlank() || it=="null"}
    private fun entries():List<JSONObject> { val a=state.getJSONArray("accounts");return (0 until a.length()).map{a.getJSONObject(it)} }
    private fun account(id:String?)=entries().find{it.getString("id")==id}
    private fun mutate(op:String,id:String="",value:String=""):Boolean {
        return try {
            val command=JSONObject().put("op",op).put("id",id).put("value",value)
            val next=JSONObject(Core.apply(state.toString(),command.toString()))
            if(next.has("error")) {toast(next.getString("error"));false}
            else if(!prefs.edit().putString("registry",next.toString()).commit()){toast("Could not save account data");false}
            else {state=next;true}
        } catch(e:Exception){toast("Could not update account");false}
    }
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    private fun round(color:Int,r:Int=14,stroke:Int=Color.TRANSPARENT)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat();setStroke(dp(2),stroke)}
    private fun button(label:String,desc:String,action:()->Unit)=TextView(this).apply {
        text=label; textSize=20f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;contentDescription=desc
        minimumWidth=dp(48);minimumHeight=dp(48);isClickable=true;isFocusable=true
        background=round(panel,12);setOnClickListener{action()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg;window.navigationBarColor=bg
        prefs=getSharedPreferences("registry",MODE_PRIVATE)
        try{state=JSONObject(prefs.getString("registry",state.toString())!!)}catch(e:Exception){
            setContentView(TextView(this).apply{text="Account metadata could not be loaded. Your existing browser profiles have not been changed.";setPadding(dp(20),dp(20),dp(20),dp(20))});return
        }
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)){
            setContentView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(32),dp(24),dp(24));addView(TextView(this@MainActivity).apply{text="Update Android System WebView\n\nThis phone’s current WebView does not support isolated account profiles. Claude Switcher will not use a shared login store.";textSize=19f});addView(button("Open Play Store","Update WebView"){openExternal(Uri.parse("market://details?id=com.google.android.webview"))})});return
        }
        val root=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setBackgroundColor(bg)}
        rail=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setBackgroundColor(panel);setPadding(dp(4),dp(8),dp(4),dp(8))}
        root.addView(rail,LinearLayout.LayoutParams(dp(62),-1))
        val scroll=ScrollView(this).apply{isFillViewport=false;isVerticalScrollBarEnabled=false}
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        scroll.addView(list);rail.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        rail.addView(button("+","Add account"){promptAdd()},LinearLayout.LayoutParams(dp(50),dp(52)))
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(body,LinearLayout.LayoutParams(0,-1,1f))
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(3),dp(4),dp(3))}
        header.addView(button("☰","Toggle account sidebar"){rail.visibility=if(rail.visibility==View.VISIBLE)View.GONE else View.VISIBLE})
        title=TextView(this).apply{textSize=16f;setTextColor(Color.WHITE);isSingleLine=true;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(dp(8),0,dp(4),0);setOnClickListener{quickSwitch()}}
        header.addView(title,LinearLayout.LayoutParams(0,dp(48),1f))
        header.addView(button("↻","Reload current page"){pages[selected()]?.reload()})
        header.addView(button("⋮","Manage account"){manage()})
        body.addView(header,LinearLayout.LayoutParams(-1,dp(54)))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;visibility=View.GONE}
        body.addView(progress,LinearLayout.LayoutParams(-1,dp(3)))
        stage=FrameLayout(this);body.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        savedPages = savedInstanceState?.getBundle("web_history") ?: Bundle()
        rail.visibility = if(savedInstanceState?.getBoolean("rail_visible",true)==false) View.GONE else View.VISIBLE
        setContentView(root)
        render()
    }
    private fun renderRail() {
        list.removeAllViews()
        entries().forEachIndexed{i,a->
            val id=a.getString("id");val name=a.getString("name");val current=id==selected()
            val row=button(name.take(2).uppercase(),name+(if(current)", selected" else "")){choose(id)}
            row.textSize=17f;row.background=round(palette[i%palette.size],if(current)13 else 26,if(current)accent else Color.TRANSPARENT)
            row.isSelected=current;row.setOnLongClickListener{manage(id);true}
            list.addView(row,LinearLayout.LayoutParams(dp(50),dp(50)).apply{bottomMargin=dp(9)})
        }
    }
    private fun render() {
        renderRail()
        val id=selected()
        title.text=account(id)?.optString("name")?:"Claude Switcher"
        title.gravity=Gravity.CENTER_VERTICAL
        pages.values.forEach{it.visibility=View.GONE}
        if(id==null){
            stage.removeAllViews();stage.addView(LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(20),0,dp(20),0)
                addView(TextView(this@MainActivity).apply{text="Your Claude workspaces";textSize=22f;gravity=Gravity.CENTER})
                addView(TextView(this@MainActivity).apply{text="Add an account to sign in on Claude’s website. Each workspace has its own browser profile.";gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(16))})
                addView(button("+ Add account","Add first account"){promptAdd()})
            },FrameLayout.LayoutParams(-1,-1))
            progress.visibility=View.GONE;return
        }
        val existing=pages[id]
        if(existing==null) {
            stage.childrenToRemove()
            try {
                val web=newPage(id)
                pages[id]=web
                stage.addView(web,FrameLayout.LayoutParams(-1,-1))
                val previous = savedPages.getBundle(id)
                savedPages.remove(id)
                val restored = if(previous != null) try { web.restoreState(previous) != null } catch(_: Exception) { false } else false
                if(!restored) web.loadUrl(account(id)?.optString("url",home)?:home)
            }catch(e:Exception){
                stage.addView(TextView(this).apply{text="Could not start an isolated workspace. Update WebView and reopen the app.";setPadding(dp(20),dp(20),dp(20),0)})
            }
        }else {existing.visibility=View.VISIBLE}
        val p=loading[id]?:100;progress.progress=p;progress.visibility=if(p<100)View.VISIBLE else View.GONE
    }
    private fun FrameLayout.childrenToRemove(){for(i in childCount-1 downTo 0)if(getChildAt(i) !is WebView)removeViewAt(i)}
    private fun choose(id:String){if(id!=selected()){picker?.onReceiveValue(null);picker=null;pickerOwner=null;if(mutate("select",id))render()}}
    private fun quickSwitch(){
        val all=entries()
        if(all.isEmpty()){promptAdd();return}
        AlertDialog.Builder(this).setTitle("Switch account").setItems(all.map{it.getString("name")}.toTypedArray()){_,i->choose(all[i].getString("id"))}.setNeutralButton("Add account"){_,_->promptAdd()}.show()
    }
    private fun promptAdd(){editName("Add account","Account "+(entries().size+1)){name->if(mutate("add",UUID.randomUUID().toString(),name))render()}}
    private fun editName(label:String,initial:String,onSave:(String)->Unit){
        val input=EditText(this).apply{setSingleLine();setText(initial);selectAll();filters=arrayOf(android.text.InputFilter.LengthFilter(40))}
        val dialog=AlertDialog.Builder(this).setTitle(label).setView(input).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val n=input.text.toString().trim();if(n.isBlank())input.error="Enter a name" else {onSave(n);dialog.dismiss()}}};dialog.show()
    }
    private fun manage(id:String?=selected()){
        val a=account(id)?:run{promptAdd();return};val key=a.getString("id")
        val options=arrayOf("Rename","Move up","Move down","Open Claude home","Remove account")
        AlertDialog.Builder(this).setTitle(a.getString("name")).setItems(options){_,i->when(i){
            0->editName("Rename account",a.getString("name")){if(mutate("rename",key,it))render()}
            1,2->if(mutate(if(i==1)"up" else "down",key))render()
            3->{choose(key);pages[key]?.loadUrl(home)}
            4->AlertDialog.Builder(this).setTitle("Remove account?").setMessage("This removes this workspace’s local login and browsing data. Your Claude account itself will not be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Remove"){_,_->remove(key)}.show()
        }}.show()
    }
    private fun remove(id:String){
        if(pickerOwner==id){picker?.onReceiveValue(null);picker=null;pickerOwner=null}
        pages.remove(id)?.let{stage.removeView(it);it.destroy()}
        try{if(!ProfileStore.getInstance().deleteProfile("workspace_"+id) && ProfileStore.getInstance().getAllProfileNames().contains("workspace_"+id))throw IllegalStateException("Profile still exists")}catch(e:Exception){toast("Unable to delete profile. Try again.");render();return}
        loading.remove(id);savedPages.remove(id);if(mutate("remove",id))render()
    }
    private fun allowed(uri:Uri):Boolean {
        val h=uri.host?.lowercase()?:return false
        return uri.scheme=="https" && (h=="claude.ai"||h.endsWith(".claude.ai")||h=="anthropic.com"||h.endsWith(".anthropic.com")||h=="accounts.google.com")
    }
    private fun openExternal(uri:Uri){try{startActivity(Intent(Intent.ACTION_VIEW,uri))}catch(e:Exception){toast("No app available for this link")}}
    private fun persist(id:String,url:String?){
        if(url==null||account(id)==null)return
        val uri=Uri.parse(url)
        if(uri.scheme=="https"&&uri.host=="claude.ai"){
            val clean=uri.buildUpon().clearQuery().fragment(null).build().toString()
            if(uri.path=="/"||uri.path=="/new"||uri.path?.startsWith("/chat/")==true||uri.path?.startsWith("/project/")==true)mutate("url",id,clean)
        }
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun newPage(id:String):WebView {
        val web=WebView(this)
        WebViewCompat.setProfile(web,"workspace_"+id)
        web.settings.apply{javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;setSupportMultipleWindows(false);javaScriptCanOpenWindowsAutomatically=false}
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                val uri=request.url
                if(allowed(uri))return false
                if(request.isForMainFrame&&request.hasGesture()&&uri.scheme=="https")openExternal(uri)
                return true
            }
            override fun doUpdateVisitedHistory(view:WebView,url:String?,isReload:Boolean){super.doUpdateVisitedHistory(view,url,isReload);persist(id,url)}
            override fun onPageFinished(view:WebView,url:String?){persist(id,url);try{WebViewCompat.getProfile(view).cookieManager.flush()}catch(_:Exception){}}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean{
                pages.remove(id);stage.removeView(view);view.destroy();if(selected()==id)render();return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView,p:Int){loading[id]=p;if(selected()==id){progress.progress=p;progress.visibility=if(p<100)View.VISIBLE else View.GONE}}
            override fun onShowFileChooser(view:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean{
                if(selected()!=id)return false
                picker?.onReceiveValue(null);picker=callback;pickerOwner=id
                return try{startActivityForResult(params.createIntent(),1001);true}catch(e:Exception){picker=null;pickerOwner=null;false}
            }
        }
        return web
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==1001){picker?.onReceiveValue(if(pickerOwner==selected())WebChromeClient.FileChooserParams.parseResult(resultCode,data)else null);picker=null;pickerOwner=null}}
    // Multi-window transitions resize existing views; never reload or recreate pages here.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pages.values.forEach { it.invalidate() }
        if(::stage.isInitialized) stage.requestLayout()
    }
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode,newConfig)
        pages.values.forEach { it.invalidate() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if(!::rail.isInitialized) return
        outState.putBoolean("rail_visible",rail.visibility==View.VISIBLE)
        val histories=Bundle()
        // Keep the active workspace first and bound total state size to avoid Binder failures.
        val ids=entries().map{it.getString("id")}.sortedBy{if(it==selected()) 0 else 1}
        var remaining=256*1024
        for(id in ids) {
            val history=try {
                val web=pages[id]
                if(web!=null) Bundle().also{web.saveState(it)} else savedPages.getBundle(id)
            } catch(_:Exception) { null } ?: continue
            val parcel=Parcel.obtain()
            val bytes=try { parcel.writeBundle(history);parcel.dataSize() } catch(_:Exception) { Int.MAX_VALUE } finally {parcel.recycle()}
            if(bytes<=remaining) { histories.putBundle(id,history);remaining-=bytes }
        }
        outState.putBundle("web_history",histories)
    }
    override fun onPause(){
        super.onPause()
        pages.forEach{(id,web)->
            try{persist(id,web.url);WebViewCompat.getProfile(web).cookieManager.flush()}catch(_:Exception){}
        }
    }
    @Deprecated("Native back navigation") override fun onBackPressed(){val w=pages[selected()];if(w?.canGoBack()==true)w.goBack() else if(::rail.isInitialized && rail.visibility==View.GONE)rail.visibility=View.VISIBLE else super.onBackPressed()}
    override fun onDestroy(){picker?.onReceiveValue(null);pages.values.forEach{stage.removeView(it);it.destroy()};pages.clear();super.onDestroy()}
}
