-- Dark Chat V9.14 - verified Google Play seller activation.
-- Deployed to existing Supabase project; preserve JazzCash and seller approval triggers.
alter table public.dc_store_requests drop constraint if exists dc_store_requests_payment_method_check;
alter table public.dc_store_requests add constraint dc_store_requests_payment_method_check
check (payment_method in ('jazzcash','google_pay','google_play_billing'));

create table if not exists public.dc_play_activation_receipts(
 token_hash text primary key check (token_hash ~ '^[0-9a-f]{64}$'),
 user_id uuid not null references auth.users(id) on delete cascade,
 order_id text,
 request_id uuid not null references public.dc_store_requests(id),
 created_at timestamptz not null default now()
);
alter table public.dc_play_activation_receipts enable row level security;
revoke all on public.dc_play_activation_receipts from public, anon, authenticated;
grant select, insert, update on public.dc_play_activation_receipts to service_role;

create or replace function public.dc_finalize_verified_play_store_purchase(
 p_owner_id uuid,p_token_hash text,p_order_id text default null
)returns boolean language plpgsql security definer set search_path='' as $fn$
declare v_existing uuid;v_request uuid;
begin
 if p_owner_id is null or p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
  raise exception 'Invalid receipt verification arguments';
 end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(p_token_hash));
 select user_id into v_existing from public.dc_play_activation_receipts where token_hash=p_token_hash;
 if found then
  if v_existing<>p_owner_id then raise exception 'Purchase already used for another account';end if;
  return true;
 end if;
 insert into public.dc_store_requests(user_id,payment_method,transfer_reference)
 values(p_owner_id,'google_play_billing','GP-'||substring(p_token_hash from 1 for 40))
 returning id into v_request;
 update public.dc_store_requests set status='approved',reviewed_at=now(),
  admin_note='Verified Google Play Developer API purchase; activated by trusted server.'
 where id=v_request;
 insert into public.dc_play_activation_receipts(token_hash,user_id,order_id,request_id)
 values(p_token_hash,p_owner_id,p_order_id,v_request);
 return true;
end $fn$;
revoke all on function public.dc_finalize_verified_play_store_purchase(uuid,text,text)
 from public,anon,authenticated;
grant execute on function public.dc_finalize_verified_play_store_purchase(uuid,text,text) to service_role;
