package com.sb24.asanzeman.request.domain.model;

/** How the bank prices a deposit (4.3): cheap deposits allow cash and a pledge at ×1.00; expensive ones only a pledge. */
public enum PriceClass {
    CHEAP,
    EXPENSIVE
}
