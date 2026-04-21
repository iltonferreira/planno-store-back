CREATE TABLE stores (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    logo_url VARCHAR(2048),
    primary_color VARCHAR(30),
    secondary_color VARCHAR(30),
    description VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

