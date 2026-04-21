alter table webhook_events add column payment_id uuid;
alter table webhook_events add column refund_id uuid;
alter table webhook_events add column order_id uuid;
alter table webhook_events add column reprocess_count integer not null default 0;
alter table webhook_events add column last_reprocessed_at timestamp with time zone;
alter table webhook_events add column last_reprocessed_by varchar(255);
alter table webhook_events add column last_reprocess_result varchar(1000);

create index idx_webhook_events_store_processed on webhook_events (store_id, processed);
create index idx_webhook_events_store_event_type on webhook_events (store_id, event_type);
