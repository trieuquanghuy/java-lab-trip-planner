package com.tripplanner.destination.destination;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "destinations_cache", schema = "destination")
public class DestinationsCacheEntity {

    @Id
    @Column(name = "provider_ref", length = 80)
    private String providerRef;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(length = 400)
    private String address;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String photos = "[]";

    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private String openingHours;

    @Column(length = 2048)
    private String website;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String raw;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected DestinationsCacheEntity() {
        // JPA requires no-arg constructor
    }

    public String getProviderRef() {
        return providerRef;
    }

    public void setProviderRef(String providerRef) {
        this.providerRef = providerRef;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhotos() {
        return photos;
    }

    public void setPhotos(String photos) {
        this.photos = photos;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
