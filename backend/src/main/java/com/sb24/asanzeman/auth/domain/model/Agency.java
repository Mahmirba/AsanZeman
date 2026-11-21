package com.sb24.asanzeman.auth.domain.model;

import java.util.UUID;

/** A travel agency allowed to register requests, as the login needs it (D35): found by its Yaghut {@code cif}. */
public record Agency(UUID id, String cif, String name, boolean active) {
}
