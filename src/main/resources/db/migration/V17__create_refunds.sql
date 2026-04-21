create table refunds (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    created_by varchar(255),
    updated_by varchar(255),
    store_id uuid not null,
    payment_id uuid not null,
    order_id uuid not null,
    provider varchar(40) not null,
    provider_refund_id varchar(120),
    type varchar(20) not null,
    amount numeric(19,2) not null,
    currency varchar(10) not null,
    status varchar(20) not null,
    reason varchar(500),
    requested_by_user_id uuid,
    requested_at timestamp with time zone not null,
    processed_at timestamp with time zone,
    raw_response varchar(20000),
    constraint fk_refunds_store foreign key (store_id) references stores (id),
    constraint fk_refunds_payment foreign key (payment_id) references payments (id),
    constraint fk_refunds_order foreign key (order_id) references orders (id),
    constraint uk_refunds_provider_refund_id unique (provider, provider_refund_id)
);

create index idx_refunds_store_payment on refunds (store_id, payment_id);
create index idx_refunds_store_order on refunds (store_id, order_id);
create index idx_refunds_store_status on refunds (store_id, status);
