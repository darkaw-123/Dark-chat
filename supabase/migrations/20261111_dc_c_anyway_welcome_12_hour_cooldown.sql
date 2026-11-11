-- Dark Chat V9.14: C Anyway welcome auto-reply limited to once per 12 hours per
-- buyer, seller and direct conversation. Live DB migration already deployed.
-- Product/category/explicit keyword responses remain available on every matching query.
-- This SQL contains no Supabase secrets, payment credentials or signing material.
CREATE TABLE IF NOT EXISTS private.dc_store_welcome_cooldowns (
  seller_id uuid NOT NULL,
  sender_id uuid NOT NULL,
  conversation_id uuid NOT NULL,
  last_sent_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (seller_id, sender_id, conversation_id)
);
REVOKE ALL ON private.dc_store_welcome_cooldowns FROM PUBLIC, anon, authenticated;
CREATE OR REPLACE FUNCTION private.dc_auto_reply()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  seller uuid; shop record; rule record; reply text; menu text; nkeys int;
  category_listing text; item_listing text; cat record; prod record; np int;
  cooldown_claimed integer;
begin
  if pg_trigger_depth() > 1 or new.deleted_at is not null or coalesce(new.message_type, 'text') <> 'text' then return new; end if;
  if not exists (select 1 from public.conversations x where x.id = new.conversation_id and x.kind = 'direct') then return new; end if;
  select cm.user_id into seller from public.conversation_members cm
    join public.dc_stores st on st.owner_id = cm.user_id
   where cm.conversation_id = new.conversation_id and cm.user_id <> new.sender_id and st.active and st.auto_reply_enabled limit 1;
  if seller is null then return new; end if;
  select * into shop from public.dc_stores where owner_id = seller;
  select * into rule from public.dc_store_keywords k
   where k.owner_id = seller and lower(trim(k.keyword)) = lower(trim(coalesce(new.body, ''))) order by k.created_at limit 1;

  if found then
    if rule.response_type = 'menu' then
      select string_agg('📂 ' || c.name || ' (' || (select count(*)::text from public.dc_store_products pp where pp.category_id = c.id and pp.owner_id = seller and pp.active) || ' products)', E'\n' order by c.created_at)
        into category_listing from public.dc_store_categories c where c.keyword_id = rule.id and c.owner_id = seller;
      select string_agg(rowtext, E'\n' order by ord) into item_listing from (
        select row_number() over (order by p.created_at desc) as ord, '• ' || p.name || ' — Rs. ' || p.price_pkr::text as rowtext
          from public.dc_store_products p join public.dc_store_categories c on c.id = p.category_id
         where c.keyword_id = rule.id and p.owner_id = seller and p.active order by p.created_at desc limit 10) x;
      reply := left(concat('🛍️ ', shop.store_name, E'\n', 'Keyword: ', rule.keyword, E'\n',
        case when nullif(trim(rule.response_text), '') is not null then left(rule.response_text, 500) || E'\n' else '' end,
        E'Categories:\n', coalesce(category_listing, 'No categories yet.'),
        case when item_listing is not null then E'\nProducts:\n' || item_listing else '' end,
        E'\nOpen the Store tab to see photos and order.'), 3750);
    elsif rule.response_type = 'text' then
      reply := rule.response_text;
    elsif rule.response_type = 'product' then
      select * into prod from public.dc_store_products where id = rule.target_id and owner_id = seller and active;
      if found then
        insert into public.messages(conversation_id, sender_id, message_type, body, store_product_id)
        values (new.conversation_id, seller, 'text',
          left('🛍️ ' || prod.name || ' — Rs. ' || prod.price_pkr::text || coalesce(case when prod.stock is null then '' when prod.stock <= 0 then ' (out of stock)' else ' (' || prod.stock || ' left)' end, '') ||
               case when nullif(trim(prod.description), '') is not null then E'\n' || prod.description else '' end, 3800), prod.id);
        reply := null;
      else
        reply := 'This product is not available right now.';
      end if;
    elsif rule.response_type = 'category' then
      select * into cat from public.dc_store_categories where id = rule.target_id and owner_id = seller;
      if found then
        select count(*) into np from public.dc_store_products where category_id = cat.id and owner_id = seller and active;
        insert into public.messages(conversation_id, sender_id, message_type, body)
        values (new.conversation_id, seller, 'text', left('📂 ' || cat.name || ' — ' || np || ' product(s)' ||
          case when nullif(trim(rule.response_text), '') is not null then E'\n' || rule.response_text else '' end, 3800));
        for prod in select * from public.dc_store_products where category_id = cat.id and owner_id = seller and active order by created_at desc limit 8 loop
          insert into public.messages(conversation_id, sender_id, message_type, body, store_product_id)
          values (new.conversation_id, seller, 'text',
            left('🛍️ ' || prod.name || ' — Rs. ' || prod.price_pkr::text || coalesce(case when prod.stock is null then '' when prod.stock <= 0 then ' (out of stock)' else ' (' || prod.stock || ' left)' end, ''), 3800), prod.id);
        end loop;
        reply := null;
      else
        reply := 'This category is not available right now.';
      end if;
    end if;
  else
    -- A general C Anyway welcome is sent once per sender / seller / direct chat
    -- per 12-hour window. Exact product/category/keyword queries still work every time.
    insert into private.dc_store_welcome_cooldowns
      (seller_id, sender_id, conversation_id, last_sent_at)
    values (seller, new.sender_id, new.conversation_id, now())
    on conflict (seller_id, sender_id, conversation_id)
    do update set last_sent_at = excluded.last_sent_at
      where private.dc_store_welcome_cooldowns.last_sent_at <= excluded.last_sent_at - interval '12 hours'
    returning 1 into cooldown_claimed;
    if cooldown_claimed is null then
      return new;
    end if;
    select count(*), string_agg('• ' || k.keyword || ' — ' ||
        case k.response_type
          when 'category' then 'for category: ' || coalesce(c.name, '?')
          when 'product' then 'for product: ' || coalesce(p.name, '?')
          when 'menu' then 'categories and products'
          else 'information' end, E'\n' order by k.keyword)
      into nkeys, menu
      from public.dc_store_keywords k
      left join public.dc_store_categories c on k.response_type = 'category' and c.id = k.target_id
      left join public.dc_store_products p on k.response_type = 'product' and p.id = k.target_id
     where k.owner_id = seller;
    reply := concat('🛍️ C Anyway · ', shop.store_name, E'\n', left(shop.welcome_message, 400), E'\n',
      case when nkeys = 0 then 'Open the Store tab to see products.'
           else E'Keywords (' || nkeys || E'):\n' || left(menu, 900) || E'\nSend one of these keywords to see it.' end);
  end if;

  if nullif(trim(coalesce(reply, '')), '') is not null then
    insert into public.messages(conversation_id, sender_id, message_type, body) values (new.conversation_id, seller, 'text', left(reply, 3800));
  end if;
  return new;
end $function$
;
