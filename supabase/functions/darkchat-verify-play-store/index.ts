import { createClient } from "npm:@supabase/supabase-js@2";

const PACKAGE = "com.darkaw.darkchat";
const PRODUCT = "darkchat_store_activation";
const HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
const reply=(status:number,body:Record<string,unknown>)=>
  new Response(JSON.stringify(body),{status,headers:HEADERS});
const b64url=(s:string)=>btoa(s).replace(/\+/g,"-").replace(/\//g,"_").replace(/=/g,"");
const bytesB64=(u:Uint8Array)=>btoa(String.fromCharCode(...u));
async function sha256Hex(value:string):Promise<string>{
  const b=new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(value)));
  return Array.from(b,x=>x.toString(16).padStart(2,"0")).join("");
}
async function playAccessToken(config:{client_email:string;private_key:string}):Promise<string>{
  const now=Math.floor(Date.now()/1000);
  const header=b64url(JSON.stringify({alg:"RS256",typ:"JWT"}));
  const payload=b64url(JSON.stringify({
    iss:config.client_email,
    scope:"https://www.googleapis.com/auth/androidpublisher",
    aud:"https://oauth2.googleapis.com/token",
    iat:now,exp:now+3600
  }));
  const claim=header+"."+payload;
  const pem=config.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,"");
  const bytes=Uint8Array.from(atob(pem),c=>c.charCodeAt(0));
  const key=await crypto.subtle.importKey("pkcs8",bytes,{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["sign"]);
  const signature=new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5",key,new TextEncoder().encode(claim)));
  const assertion=claim+"."+bytesB64(signature).replace(/\+/g,"-").replace(/\//g,"_").replace(/=/g,"");
  const result=await fetch("https://oauth2.googleapis.com/token",{
    method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},
    body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion})
  });
  const json=await result.json();
  if(!result.ok||typeof json.access_token!=="string")throw new Error("Google Play authorization failed");
  return json.access_token;
}
Deno.serve(async(req:Request)=>{
  if(req.method==="OPTIONS")return new Response(null,{headers:HEADERS});
  if(req.method!=="POST")return reply(405,{ok:false,error:"Method not allowed"});
  const supabaseUrl=Deno.env.get("SUPABASE_URL")||"";
  const anonKey=Deno.env.get("SUPABASE_ANON_KEY")||
    JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")||"{}").default;
  const adminKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||
    JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS")||"{}").default;
  if(!supabaseUrl||!anonKey||!adminKey)return reply(503,{ok:false,error:"Supabase server configuration is unavailable"});
  const bearer=req.headers.get("Authorization")||"";
  const jwt=bearer.startsWith("Bearer ")?bearer.slice(7):"";
  if(!jwt)return reply(401,{ok:false,error:"Sign in before verifying a purchase"});
  const db=createClient(supabaseUrl,anonKey,{auth:{persistSession:false}});
  const {data:userInfo,error:authError}=await db.auth.getUser(jwt);
  const user=userInfo?.user;
  if(authError||!user)return reply(401,{ok:false,error:"Your session is no longer valid"});
  let input:Record<string,unknown>;
  try{input=await req.json();}catch{return reply(400,{ok:false,error:"Invalid JSON"});}
  const purchaseToken=typeof input.purchase_token==="string"?input.purchase_token:"";
  if(purchaseToken.length<15||purchaseToken.length>4096||!/^[\w.\-]+$/.test(purchaseToken))
    return reply(400,{ok:false,error:"Invalid Google Play purchase token"});
  const serviceJson=Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
  if(!serviceJson)return reply(503,{ok:false,error:"Google Play receipt verification is not yet configured. Contact the developer."});
  try{
    const service=JSON.parse(serviceJson);
    if(!service.client_email||!service.private_key)throw new Error("Missing Google Play service credentials");
    const token=await playAccessToken(service);
    const url="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
      +encodeURIComponent(PACKAGE)+"/purchases/productsv2/tokens/"+encodeURIComponent(purchaseToken);
    const response=await fetch(url,{headers:{Authorization:"Bearer "+token}});
    if(!response.ok) {
      console.error("Google Play verify rejected",response.status);
      return reply(403,{ok:false,error:"Google Play could not confirm this purchase"});
    }
    const verified=await response.json();
    if(verified.purchaseStateContext?.purchaseState!=="PURCHASED")
      return reply(409,{ok:false,error:"Purchase is pending, cancelled, or not completed"});
    if(!Array.isArray(verified.productLineItem)||!verified.productLineItem.some(
       (item:{productId?:string})=>item.productId===PRODUCT))
      return reply(403,{ok:false,error:"The receipt is not for Dark Chat Store Activation"});
    const accountId=await sha256Hex(user.id.toLowerCase());
    if(verified.obfuscatedExternalAccountId!==accountId)
      return reply(403,{ok:false,error:"The receipt belongs to another Dark Chat account"});
    const tokenHash=await sha256Hex(purchaseToken);
    const admin=createClient(supabaseUrl,adminKey,{auth:{persistSession:false}});
    const {data:finalized,error:dbError}=await admin.rpc("dc_finalize_verified_play_store_purchase",{
      p_owner_id:user.id,p_token_hash:tokenHash,p_order_id:verified.orderId||null
    });
    if(dbError||finalized!==true){
      console.error("Verified Play receipt could not activate store",dbError?.message);
      return reply(503,{ok:false,error:"Purchase verified but store activation needs a retry. Please use Restore Purchase."});
    }
    // Acknowledge ONLY after secure entitlement grant. Repeating this endpoint is idempotent.
    let acknowledged=verified.acknowledgementState==="ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";
    if(!acknowledged){
      const ackUrl="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
        +encodeURIComponent(PACKAGE)+"/purchases/products/"+encodeURIComponent(PRODUCT)
        +"/tokens/"+encodeURIComponent(purchaseToken)+":acknowledge";
      const ack=await fetch(ackUrl,{method:"POST",headers:{
        Authorization:"Bearer "+token,"Content-Type":"application/json"},body:"{}"});
      acknowledged=ack.ok;
      if(!acknowledged)console.error("Google Play acknowledgement requires retry",ack.status);
    }
    return reply(200,{ok:true,store_active:true,acknowledged,
      message:acknowledged?"Store activated and Google Play purchase acknowledged":
        "Store activated. Reopen the app and Restore Purchase to retry acknowledgement."});
  }catch(error){
    console.error("Google Play purchase verification error",String(error));
    return reply(503,{ok:false,error:"Purchase verification temporarily unavailable. Try Restore Purchase later."});
  }
});
