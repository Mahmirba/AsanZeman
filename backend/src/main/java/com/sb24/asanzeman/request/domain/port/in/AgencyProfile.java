package com.sb24.asanzeman.request.domain.port.in;

/** Who the agency panel shows at the top of the page (SRS UC-AZS-13): the agency name and its 4-digit code. */
public record AgencyProfile(String name, String code) {
}
