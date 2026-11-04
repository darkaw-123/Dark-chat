/* Dark Chat Store v1.5 - additive module, no payment credentials in client */
(function(){
'use strict';
const h=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>'Rs. '+Number(n||0).toLocaleString('en-PK',{maximumFractionDigits:2});
const photo=p=>p?sb.storage.from('darkchat-store-images').getPublicUrl(p).data.publicUrl:'';
const isHttps=u=>/^https:\/\//i.test(String(u||''));
let settings=null,stores=[],myStore=null,cats=[],products=[],rules=[],orders=[],requests=[],view='my',picked=null,cart=[],loading=false,orderChannel=null,requestChannel=null;
function root(){return document.getElementById('dc-store-root')}
function btn(action,text,extra){return '<button type="button" class="btn '+(extra||'g')+'" data-s="'+action+'">'+text+'</button>'}
function card(body){return '<section class="dcsc">'+body+'</section>'}
function input(name,label,value,required,max){return '<label>'+h(label)+'</label><input name="'+h(name)+'" value="'+h(value||'')+'" maxlength="'+(max||160)+'" '+(required?'required':'')+'>'}
function area(name,label,value){return '<label>'+h(label)+'</label><textarea name="'+h(name)+'" maxlength="2000" rows="3">'+h(value||'')+'</textarea>'}
function message(t,bad){try{toast(t,!!bad)}catch(_){alert(t)}}
function tabs(){if(!myStore)return view==='browse'||view==='cart'?'<div class="dcstabs">'+btn('my','Store Activation')+btn('browse','Browse Stores')+btn('cart','Cart ('+cart.reduce((a,x)=>a+x.quantity,0)+')')+'</div>':'';return '<div class="dcstabs">'+btn('my','My Store')+btn('browse','Explore')+btn('cart','Cart ('+cart.reduce((a,x)=>a+x.quantity,0)+')')+'</div>'}
function readCart(){try{cart=JSON.parse(localStorage.getItem('dcstore_cart_'+me.id)||'[]');if(!Array.isArray(cart))cart=[]}catch(_){cart=[]}}
function saveCart(){try{localStorage.setItem('dcstore_cart_'+me.id,JSON.stringify(cart))}catch(_){}}
function confirmErr(error){if(error)throw new Error(error.message)}
async function fetchData(){
 const [ss,st,pr,req]=await Promise.all([
  sb.from('dc_store_settings').select('*').eq('id','global').maybeSingle(),
  sb.from('dc_stores').select('*').eq('active',true).order('created_at',{ascending:false}),
  sb.from('dc_store_products').select('*').eq('active',true).order('created_at',{ascending:false}).limit(500),
  sb.from('dc_store_requests').select('*').eq('user_id',me.id).order('created_at',{ascending:false}).limit(4)
 ]);
 confirmErr(ss.error);confirmErr(st.error);confirmErr(pr.error);confirmErr(req.error);
 settings=ss.data||{store_price_pkr:1500,jazzcash_number:'03067008420',jazzcash_account_name:'Abdul Waheed'};
 stores=st.data||[];products=pr.data||[];requests=req.data||[];
 myStore=stores.find(x=>x.owner_id===me.id)||null;
 if(myStore){
  const [c,k,o]=await Promise.all([
   sb.from('dc_store_categories').select('*').eq('owner_id',me.id).order('created_at'),
   sb.from('dc_store_keywords').select('*').eq('owner_id',me.id).order('created_at'),
   sb.from('dc_store_orders').select('*').eq('store_owner_id',me.id).order('created_at',{ascending:false}).limit(150)
  ]);
  confirmErr(c.error);confirmErr(k.error);confirmErr(o.error);
  cats=c.data||[];rules=k.data||[];orders=o.data||[];
 }
 if(!picked&&stores.length)picked=stores[0].owner_id;
}
function picture(path){return path?'<img class="dcspic" alt="Store item" src="'+h(photo(path))+'" loading="lazy">':'<div class="dcsempty">🛍️</div>'}
function itemTile(p){
 return '<div class="dcsproduct">'+picture(p.image_path)+'<b>'+h(p.name)+'</b><span class="dcsprice">'+money(p.price_pkr)+'</span>'+
 '<p class="dcsdim">'+h(p.description).slice(0,160)+'</p><div class="dcsactions"><button data-s="add" data-id="'+p.id+'" class="btn g s">Add to Cart</button><button data-s="buy" data-id="'+p.id+'" class="btn s">Buy Now</button></div></div>';
}
function renderBrowse(){
 const listing=stores.map(s=>'<button class="dcsstore" data-s="pick" data-id="'+s.owner_id+'">🏪 <b>'+h(s.store_name)+'</b><small>'+h(s.description).slice(0,100)+'</small></button>').join('');
 const s=stores.find(x=>x.owner_id===picked);
 const selectedProducts=products.filter(p=>p.owner_id===picked);
 return card('<h3>Explore Stores</h3><p class="dcsdim">Shop directly inside Dark Chat. Orders go to the seller.</p><div class="dcsstores">'+(listing||'<p>No stores have opened yet.</p>')+'</div>')+
 (s?card('<h3>🏪 '+h(s.store_name)+'</h3><p>'+h(s.description)+'</p><p class="dcsdim">C Anyway — chat automation available if the owner enables it.</p><div class="dcsgrid">'+
 (selectedProducts.map(itemTile).join('')||'<p>No products yet.</p>')+'</div>'):'');
}
function requestPanel(){
 const last=requests[0],pending=last?.status==='pending',rejected=last?.status==='rejected';
 const gpay=isHttps(settings.google_pay_checkout_url),price=money(settings.store_price_pkr);
 const how='<div class="dcssteps"><div><span>01</span><b>Make Payment</b><small>Transfer the exact amount to JazzCash.</small></div><div><span>02</span><b>Send Request</b><small>Submit your real transaction ID.</small></div><div><span>03</span><b>Admin Approval</b><small>Store tools unlock after verification.</small></div></div>';
 const footer=card('<div class="dcsafter"><h4>🛍️ Want to shop instead?</h4><p>Customers can browse other stores without purchasing a seller account.</p>'+btn('browse','Browse available stores')+'</div>');
 const heading='<div class="dcspayhero"><div class="dcspill">✦ DARK CHAT MARKETPLACE</div><div class="dcspayemblem">🏪</div><h2>Start Your Own Store</h2><p>Sell products directly through your Dark Chat profile. Your store dashboard is protected until payment is verified by an administrator.</p><div class="dcstotal"><small>One-time store activation</small><strong>'+price+'</strong><span>PKR · Manually approved</span></div></div>';
 if(pending)return '<div class="dcsflow">'+heading+
   card('<div class="dcsstatus dcswaiting"><span class="dcsstatusicon">⏳</span><h3>Payment Request Submitted</h3><p>Your store is <b>not active yet</b>. The administrator will check the transaction and decide whether to approve it.</p><div class="dcsreceipt"><div><span>Status</span><strong>Pending Verification</strong></div><div><span>Method</span><strong>'+h(last.payment_method)+'</strong></div><div><span>Reference</span><strong>'+h(last.transfer_reference)+'</strong></div><div><span>Amount</span><strong>'+money(last.amount_pkr)+'</strong></div></div>'+
   '<p class="dcsdim">Do not pay twice for this request. Once admin approves it, your Store dashboard will unlock.</p><button class="btn" data-s="reload" style="width:100%">↻ Check Approval Status</button></div>')+
   how+footer+'</div>';
 return '<div class="dcsflow">'+heading+how+
  card('<div class="dcspaytitle"><span>💳</span><div><h3>Pay to activate your store</h3><p class="dcsdim">Only the payment section is shown before approval.</p></div></div>'+
  (rejected?'<div class="dcsstatus dcsrejected"><strong>Previous request rejected.</strong><p>Check your payment information and use a valid reference before sending a new request.</p></div>':'')+
  '<div class="dcspay"><div class="dcspaytop"><span>📲 JazzCash Transfer</span><strong>'+price+'</strong></div><div class="dcspayline"><span>Account number</span><strong>'+h(settings.jazzcash_number)+'</strong></div><div class="dcspayline"><span>Account holder</span><strong>'+h(settings.jazzcash_account_name)+'</strong></div>'+
  '<button class="btn g" type="button" data-s="copy-number">Copy JazzCash Number</button></div>'+
  '<p class="dcsdim">Open JazzCash on your phone, send '+price+' to this number, and enter the transaction reference shown on your receipt. Submitting a reference does not verify a payment.</p>'+
  '<form data-f="request"><label>Payment Method</label><select name="payment_method"><option value="jazzcash">JazzCash — manual verification</option>'+
  (gpay?'<option value="google_pay">Google Pay — pending admin verification</option>':'')+'</select>'+
  input('transfer_reference','Transaction / Payment Reference','',true,90)+
  '<button class="btn" type="submit" style="width:100%;margin-top:16px">Submit Payment for Approval</button></form>'+
  (gpay?'<p class="dcsdim">Google Pay gateway: <a href="'+h(settings.google_pay_checkout_url)+'" target="_blank" rel="noopener">Open merchant checkout</a>. Confirmation remains manual.</p>':
    '<p class="dcsdim">Google Pay is not available until the administrator configures an authorised payment gateway.</p>')+
  '<div class="dcsnotice">🔒 Your store name, products, categories, automated replies and order dashboard appear <b>only after the administrator approves your request.</b></div>')+
  footer+'</div>';
}
function renderMy(){
 if(!myStore)return requestPanel();
 const addcat=card('<h3>Categories</h3><form data-f="category">'+input('name','Category name','',true,80)+
 '<label>Category photo (optional)</label><input name="image" type="file" accept="image/png,image/jpeg,image/webp,image/gif">'+
 '<button type="submit" class="btn s">Add Category</button></form><div class="dcslist">'+cats.map(c=>
 '<div>'+picture(c.image_path)+'<span>'+h(c.name)+'</span><button class="btn r s" data-s="delcat" data-id="'+c.id+'">Remove</button></div>').join('')+'</div>');
 const addprod=card('<h3>Products</h3><form data-f="product">'+input('name','Product name','',true,120)+
 input('price_pkr','Price in Pakistani rupees','',true,15)+
 area('description','Description','')+
 '<label>Category</label><select name="category_id"><option value="">Uncategorised</option>'+cats.map(c=>'<option value="'+c.id+'">'+h(c.name)+'</option>').join('')+'</select>'+
 '<label>Product photo</label><input name="image" type="file" accept="image/png,image/jpeg,image/webp,image/gif">'+
 '<button type="submit" class="btn s">Add Product</button></form>'+
 '<div class="dcsgrid">'+products.filter(p=>p.owner_id===me.id).map(p=>'<div class="dcsproduct">'+picture(p.image_path)+'<b>'+h(p.name)+'</b><span>'+money(p.price_pkr)+'</span><button class="btn r s" data-s="delprod" data-id="'+p.id+'">Remove</button></div>').join('')+'</div>');
 const keywords=card('<h3>C Anyway — Auto Reply Keywords</h3><p class="dcsdim">When somebody sends you a private message, they receive your welcome menu, or the matching keyword response. Example: 1 → product, 2 → category, hi → message.</p>'+
 '<form data-f="keyword">'+input('keyword','Keyword (e.g. 1, 2, price, hi)','',true,40)+
 '<label>Response type</label><select name="response_type"><option value="text">Custom text / link</option><option value="product">Product details</option><option value="category">Category products</option></select>'+
 area('response_text','Response text or link (for Custom text)','')+
 '<label>Related product or category</label><select name="target_id"><option value="">None / custom text</option>'+
 cats.map(c=>'<option value="'+c.id+'">Category: '+h(c.name)+'</option>').join('')+
 products.filter(p=>p.owner_id===me.id).map(p=>'<option value="'+p.id+'">Product: '+h(p.name)+'</option>').join('')+
 '</select><button type="submit" class="btn s">Save Keyword</button></form><div class="dcslist">'+
 rules.map(k=>'<div><span><b>'+h(k.keyword)+'</b> → '+h(k.response_type)+' · '+h(k.response_text).slice(0,65)+'</span><button class="btn r s" data-s="delkey" data-id="'+k.id+'">Delete</button></div>').join('')+'</div>');
 const orderList=card('<h3>New Orders <span class="chip">'+orders.length+'</span></h3><p class="dcsdim">Orders appear here automatically when someone checks out.</p>'+
 orders.map(o=>'<div class="dcsorder"><strong>'+h((products.find(p=>p.id===o.product_id)||{}).name||'Product')+'</strong> · '+o.quantity+
 ' × '+money(o.unit_price_pkr)+'<p>'+h(o.customer_name)+' · '+h(o.customer_phone)+'<p>'+h(o.delivery_address)+'</p>'+
 '<small>Order '+h(o.id.slice(0,8))+' · '+h(o.created_at.slice(0,10))+'</small>'+
 '<label>Status</label><select data-s="status" data-id="'+o.id+'">'+['new','accepted','shipped','completed','cancelled'].map(st=>'<option value="'+st+'" '+(o.status===st?'selected':'')+'>'+st+'</option>').join('')+'</select></div>').join('')+
 (orders.length?'':'<p>No orders yet.</p>'));
 return card('<h3>My Store — '+h(myStore.store_name)+'</h3><form data-f="profile">'+
 input('store_name','Store name',myStore.store_name,true,70)+
 area('description','About your store',myStore.description)+
 area('welcome_message','C Anyway welcome reply',myStore.welcome_message)+
 '<label><input type="checkbox" name="auto_reply_enabled" '+(myStore.auto_reply_enabled?'checked':'')+'> Enable C Anyway automatic replies</label>'+
 '<button type="submit" class="btn">Save Store Profile</button></form>')+
 addcat+addprod+keywords+orderList;
}
function cartProducts(){return cart.map(item=>({item,product:products.find(p=>p.id===item.id)})).filter(x=>x.product)}
function renderCart(){
 const items=cartProducts();
 const total=items.reduce((sum,x)=>sum+Number(x.product.price_pkr)*x.item.quantity,0);
 return card('<h3>Shopping Cart</h3>'+(items.length?
 items.map(x=>'<div class="dcsorder"><b>'+h(x.product.name)+'</b> · '+x.item.quantity+' × '+money(x.product.price_pkr)+
 '<button class="btn r s" data-s="remove" data-id="'+x.item.id+'">Remove</button></div>').join('')+
 '<h3>Total: '+money(total)+'</h3><form data-f="checkout">'+
 input('customer_name','Full name',prof?.display_name||'',true,120)+
 input('customer_phone','Phone number','',true,28)+area('delivery_address','Full delivery address','')+
 area('notes','Optional order notes','')+
 '<button class="btn" type="submit" style="width:100%">Place Order</button></form>'+
 '<p class="dcsdim">Placing an order sends your details to the store owner. Product payment/delivery is arranged with the seller separately. No payment is taken by this screen.</p>'
 :'<p>Your cart is empty. Browse stores to add products.</p>'));
}
async function render(){
 if(!me||!root())return;
 if(loading)return;loading=true;
 const node=root();node.innerHTML='<p class="dcsdim">Loading stores...</p>';
 try{
  readCart();await fetchData();
  if(!node.isConnected)return;
  node.innerHTML=tabs()+(view==='my'?renderMy():view==='cart'?renderCart():renderBrowse());
  attachRealtime();attachRequestRealtime();
 }catch(e){node.innerHTML=card('<h3>Store unavailable</h3><p>'+h(e.message)+'</p>'+btn('reload','Try again'))}
 finally{loading=false}
}
async function upload(file,type){
 if(!file||!file.size)return null;
 if(file.size>5*1048576||!['image/png','image/jpeg','image/webp','image/gif'].includes(file.type))
  throw new Error('Choose a PNG, JPG, WEBP or GIF under 5 MB.');
 const ext={'image/png':'png','image/jpeg':'jpg','image/webp':'webp','image/gif':'gif'}[file.type];
 const path=me.id+'/'+type+'/'+Date.now()+'-'+Math.random().toString(36).slice(2,10)+'.'+ext;
 const {error}=await sb.storage.from('darkchat-store-images').upload(path,file,{contentType:file.type});
 confirmErr(error);return path;
}
async function submit(form){
 const type=form.dataset.f,d=new FormData(form),val=n=>String(d.get(n)||'').trim(),owner=me.id;
 const submitBtn=form.querySelector('[type=submit]');if(submitBtn)submitBtn.disabled=true;
 try{
  if(type==='request'){
   if(requests.some(x=>x.status==='pending'))throw new Error('You already have a pending request. Wait for admin approval.');
   const reference=val('transfer_reference');
   if(!reference)throw new Error('Enter a transaction reference');
   const method=val('payment_method');
   if(method==='google_pay'&&!isHttps(settings.google_pay_checkout_url))throw new Error('Google Pay is not configured');
   const {error}=await sb.from('dc_store_requests').insert({user_id:owner,payment_method:method,transfer_reference:reference});
   confirmErr(error);message('Store request submitted. Admin must verify your payment.');
  }else if(type==='profile'){
   const {error}=await sb.from('dc_stores').update({
    store_name:val('store_name'),description:val('description'),
    welcome_message:val('welcome_message'),
    auto_reply_enabled:d.has('auto_reply_enabled')
   }).eq('owner_id',owner);confirmErr(error);message('Store profile saved');
  }else if(type==='category'){
   const path=await upload(d.get('image'),'categories');
   const {error}=await sb.from('dc_store_categories').insert({owner_id:owner,name:val('name'),image_path:path});
   confirmErr(error);message('Category added');
  }else if(type==='product'){
   const price=Number(val('price_pkr'));if(!Number.isFinite(price)||price<=0)throw new Error('Enter a valid price');
   const path=await upload(d.get('image'),'products');
   const {error}=await sb.from('dc_store_products').insert({owner_id:owner,
    name:val('name'),price_pkr:price,description:val('description'),
    category_id:val('category_id')||null,image_path:path});
   confirmErr(error);message('Product added');
  }else if(type==='keyword'){
   const keyword=val('keyword'),rt=val('response_type'),target=val('target_id');
   const row={owner_id:owner,keyword,response_type:rt,response_text:val('response_text'),target_id:rt==='text'?null:target||null};
   if(rt!=='text'&&!row.target_id)throw new Error('Choose a linked product or category');
   if(rt==='text'&&!row.response_text)throw new Error('Enter the automatic reply text or link');
   const {error}=await sb.from('dc_store_keywords').upsert(row,{onConflict:'owner_id,keyword'});
   confirmErr(error);message('C Anyway keyword saved');
  }else if(type==='checkout'){
   const items=cartProducts(),name=val('customer_name'),phone=val('customer_phone');
   const address=val('delivery_address'),notes=val('notes');
   if(!items.length)throw new Error('Cart is empty');
   if(name.length<2||phone.length<7)throw new Error('Name and phone number are required');
   const group=crypto.randomUUID();
   const data=items.map(x=>({
    checkout_id:group,product_id:x.product.id,store_owner_id:x.product.owner_id,
    buyer_id:owner,quantity:x.item.quantity,customer_name:name,
    customer_phone:phone,delivery_address:address,notes:notes
   }));
   const {data:placed,error}=await sb.from('dc_store_orders').insert(data).select('id');
   confirmErr(error);cart=[];saveCart();
   message('Order placed! The seller can view your details in their Orders panel.');
   (placed||[]).forEach(x=>sb.functions.invoke('darkchat-send-push',{body:{order_id:x.id}}).catch(()=>{}));
   view='browse';
  }
  await render();
 }catch(e){message(e.message||'Request failed',true);if(submitBtn)submitBtn.disabled=false}
}
async function action(button){
 const op=button.dataset.s,id=button.dataset.id;
 if(['browse','my','cart'].includes(op)){view=op;return render()}
 if(op==='reload')return render();
 if(op==='copy-number'){try{await navigator.clipboard.writeText(settings.jazzcash_number);message('JazzCash number copied.')}catch(_){message('Copy the number shown above.')}return}
 if(op==='pick'){picked=id;return render()}
 if(op==='add'||op==='buy'){
  const p=products.find(x=>x.id===id);if(!p)return message('Product not found',true);
  if(p.owner_id===me.id)return message('You cannot order your own product',true);
  const existing=cart.find(x=>x.id===id);
  if(existing)existing.quantity=Math.min(99,existing.quantity+1);else cart.push({id,quantity:1});
  saveCart();message('Added to cart');if(op==='buy'){view='cart';await render()}else{const n=root().querySelector('[data-s=cart]');if(n)n.textContent='Cart ('+cart.reduce((a,x)=>a+x.quantity,0)+')'}
  return;
 }
 if(op==='remove'){cart=cart.filter(x=>x.id!==id);saveCart();return render()}
 if(['delcat','delprod','delkey'].includes(op)){
  if(!confirm('Delete this item?'))return;
  const table={delcat:'dc_store_categories',delprod:'dc_store_products',delkey:'dc_store_keywords'}[op];
  const {error}=await sb.from(table).delete().eq('id',id).eq('owner_id',me.id);
  if(error)return message(error.message,true);message('Deleted');return render();
 }
}
async function change(select){
 if(select.dataset.s!=='status')return;
 const {error}=await sb.from('dc_store_orders').update({status:select.value})
  .eq('id',select.dataset.id).eq('store_owner_id',me.id);
 if(error)message(error.message,true);else message('Order updated');
}
function attachRequestRealtime(){
 if(requestChannel||!me)return;
 requestChannel=sb.channel('dc-store-activation-'+me.id)
 .on('postgres_changes',{event:'UPDATE',schema:'public',table:'dc_store_requests',filter:'user_id=eq.'+me.id},payload=>{
   if(payload.new?.status==='approved')message('✅ Store approved! Your seller dashboard is now unlocked.');
   else if(payload.new?.status==='rejected')message('Your store payment request was rejected.',true);
   if(root())render();
 })
 .subscribe();
}
function attachRealtime(){
 if(!myStore||orderChannel)return;
 orderChannel=sb.channel('dc-seller-orders-'+me.id)
 .on('postgres_changes',{event:'INSERT',schema:'public',table:'dc_store_orders',filter:'store_owner_id=eq.'+me.id},()=>{message('🛍️ New store order received!');if(view==='my'&&root())render()})
 .subscribe();
}
function newsBanner(){
 if(!settings?.ad_enabled||!isHttps(settings.ad_banner_url))return '';
 const image='<img src="'+h(settings.ad_banner_url)+'" alt="Sponsored banner" loading="lazy" style="display:block;width:100%;border-radius:13px;max-height:220px;object-fit:contain">';
 return '<div class="ann" style="margin:10px"><small style="color:#c8adff">Sponsored</small><b style="display:block">'+h(settings.ad_title||'Sponsored')+'</b>'+
 (isHttps(settings.ad_target_url)?'<a href="'+h(settings.ad_target_url)+'" target="_blank" rel="noopener sponsored">'+image+'</a>':image)+'</div>';
}
window.DCStore={render,newsBanner};
document.addEventListener('click',e=>{const b=e.target.closest('[data-s]');if(!b||!b.closest('#dc-store-root'))return;e.preventDefault();action(b).catch(x=>message(x.message,true))});
document.addEventListener('submit',e=>{const f=e.target.closest('#dc-store-root form[data-f]');if(!f)return;e.preventDefault();submit(f)});
document.addEventListener('change',e=>{const b=e.target.closest('#dc-store-root [data-s=status]');if(b)change(b)});
sb.auth.onAuthStateChange((event,session)=>{
 if(event==='SIGNED_OUT'){settings=null;stores=[];myStore=null;cart=[];view='my';if(orderChannel){sb.removeChannel(orderChannel);orderChannel=null}if(requestChannel){sb.removeChannel(requestChannel);requestChannel=null}}
 if((event==='SIGNED_IN'||event==='INITIAL_SESSION')&&session)setTimeout(async()=>{try{const {data}=await sb.from('dc_store_settings').select('*').eq('id','global').single();settings=data;if(typeof tab!=='undefined'&&tab==='news')renderNewsAgain()}catch(_){}},500)
});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&typeof tab!=='undefined'&&tab==='store'&&me&&root())render()});
function renderNewsAgain(){try{if(typeof window.render==='function')window.render()}catch(_){}}
})();