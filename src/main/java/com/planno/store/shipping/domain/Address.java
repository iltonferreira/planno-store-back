package com.planno.store.shipping.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.order.domain.Order;
import com.planno.store.store.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "addresses")
public class Address extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "cart_id")
    private UUID cartId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @Column(nullable = false, length = 160)
    private String recipientName;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 50)
    private String number;

    @Column(length = 255)
    private String complement;

    @Column(nullable = false, length = 120)
    private String neighborhood;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 80)
    private String country;

    protected Address() {
    }

    private Address(
            Store store,
            UUID cartId,
            Order order,
            String recipientName,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode,
            String country
    ) {
        this.store = store;
        this.cartId = cartId;
        this.order = order;
        this.recipientName = recipientName.trim();
        this.street = street.trim();
        this.number = number.trim();
        this.complement = blankToNull(complement);
        this.neighborhood = neighborhood.trim();
        this.city = city.trim();
        this.state = state.trim();
        this.postalCode = postalCode.trim();
        this.country = country.trim();
    }

    public static Address forOrder(
            Store store,
            UUID cartId,
            Order order,
            String recipientName,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode,
            String country
    ) {
        return new Address(store, cartId, order, recipientName, street, number, complement, neighborhood, city, state, postalCode, country);
    }

    public Store getStore() {
        return store;
    }

    public UUID getCartId() {
        return cartId;
    }

    public Order getOrder() {
        return order;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

