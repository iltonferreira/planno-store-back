alter table stores
    add column if not exists banner_url varchar(2048);

alter table stores
    add column if not exists commission_rate numeric(5, 2) not null default 2.50;
