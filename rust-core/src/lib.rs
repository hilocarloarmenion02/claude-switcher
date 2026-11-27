use jni::{JNIEnv, objects::{JClass,JString}, sys::jstring};
use serde::{Deserialize,Serialize};
#[derive(Clone,Debug,Serialize,Deserialize)]
struct Account { id:String, name:String, url:String }
#[derive(Default,Debug,Serialize,Deserialize)]
struct Registry { accounts:Vec<Account>, selected:Option<String> }
#[derive(Deserialize)]
struct Command { op:String, #[serde(default)] id:String, #[serde(default)] value:String }
fn safe_url(s:&str)->bool {
    s=="https://claude.ai" || s.starts_with("https://claude.ai/")
}
fn change(mut r:Registry,c:Command)->Result<Registry,String> {
    let pos=r.accounts.iter().position(|a|a.id==c.id);
    match c.op.as_str() {
        "add" => {
            if pos.is_some() || c.id.is_empty() || !c.id.chars().all(|x|x.is_ascii_alphanumeric()||x=='-') || r.accounts.len()>=32 {return Err("Invalid account or account limit reached".into())}
            let name=c.value.trim();
            if name.is_empty() || name.chars().count()>40 {return Err("Name must contain 1–40 characters".into())}
            r.accounts.push(Account{id:c.id.clone(),name:name.into(),url:"https://claude.ai/".into()});
            r.selected=Some(c.id);
        },
        "select" => {if pos.is_none(){return Err("Unknown account".into())} r.selected=Some(c.id);},
        "rename" => {let a=&mut r.accounts[pos.ok_or("Unknown account")?];let n=c.value.trim();if n.is_empty()||n.chars().count()>40{return Err("Name must contain 1–40 characters".into())} a.name=n.into();},
        "url" => {if safe_url(&c.value){r.accounts[pos.ok_or("Unknown account")?].url=c.value;}},
        "up" => {let i=pos.ok_or("Unknown account")?;if i>0{r.accounts.swap(i,i-1)}},
        "down" => {let i=pos.ok_or("Unknown account")?;if i+1<r.accounts.len(){r.accounts.swap(i,i+1)}},
        "remove" => {let i=pos.ok_or("Unknown account")?;r.accounts.remove(i);if r.selected.as_deref()==Some(&c.id){r.selected=r.accounts.get(i.min(r.accounts.len().saturating_sub(1))).map(|a|a.id.clone());}},
        _ => return Err("Unknown operation".into())
    }
    Ok(r)
}
#[no_mangle]
pub extern "system" fn Java_com_claudeswitcher_app_Core_apply(mut env:JNIEnv,_class:JClass,state:JString,cmd:JString)->jstring {
    let result=(||->Result<String,String>{
        let s:String=env.get_string(&state).map_err(|e|e.to_string())?.into();
        let c:String=env.get_string(&cmd).map_err(|e|e.to_string())?.into();
        let r=serde_json::from_str(&s).map_err(|e|e.to_string())?;
        let cmd=serde_json::from_str(&c).map_err(|e|e.to_string())?;
        serde_json::to_string(&change(r,cmd)?).map_err(|e|e.to_string())
    })();
    let out=result.unwrap_or_else(|e|serde_json::json!({"error":e}).to_string());
    env.new_string(out).map(|s|s.into_raw()).unwrap_or(std::ptr::null_mut())
}
#[cfg(test)] mod tests {
 use super::*;
 fn cmd(op:&str,id:&str,value:&str)->Command{Command{op:op.into(),id:id.into(),value:value.into()}}
 #[test] fn registry(){let r=change(Registry::default(),cmd("add","a","Work")).unwrap();let r=change(r,cmd("add","b","Personal")).unwrap();let r=change(r,cmd("select","a","")).unwrap();let r=change(r,cmd("url","b","https://claude.ai/chat/abc")).unwrap();assert_eq!(r.accounts[0].url,"https://claude.ai/");assert_eq!(r.selected.as_deref(),Some("a"));let r=change(r,cmd("remove","a","")).unwrap();assert_eq!(r.selected.as_deref(),Some("b"));}
 #[test] fn blocks_invalid(){assert!(!safe_url("https://claude.ai.evil.example/"));assert!(change(Registry::default(),cmd("add","../bad","x")).is_err());}
}
