CREATE TABLE categories (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_categories_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT uq_categories_store_slug UNIQUE (store_id, slug)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    category_id UUID,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    description VARCHAR(4000),
    status VARCHAR(30) NOT NULL,
    base_price NUMERIC(19, 2) NOT NULL,
    main_image_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_products_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uq_products_store_slug UNIQUE (store_id, slug),
    CONSTRAINT uq_products_store_sku UNIQUE (store_id, sku)
);

