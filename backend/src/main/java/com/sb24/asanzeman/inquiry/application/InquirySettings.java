package com.sb24.asanzeman.inquiry.application;

import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * The chain's shape (D26, D27): the stages, each a set of inquiries that run side by side, and each stage's deadline.
 * {@code azs.inquiry.stages} and {@code azs.inquiry.stage-deadlines} in the configuration.
 */
public record InquirySettings(List<List<InquiryType>> stages, List<Duration> deadlines) {

    /**
     * Reads the configuration text: stages are separated by {@code ;} and the inquiries of a stage by {@code ,}, as in
     * {@code SABT_AHVAL;SHAHKAR,SEPAD,BLACKLIST}.
     */
    public static InquirySettings parse(String stages, List<Duration> deadlines) {
        List<List<InquiryType>> parsed = Arrays.stream(stages.split(";"))
                .map(stage -> Arrays.stream(stage.split(",")).map(String::trim)
                        .map(name -> {
                            try {
                                return InquiryType.valueOf(name);
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("azs.inquiry.stages names an unknown inquiry: " + name);
                            }
                        }).toList())
                .toList();
        return new InquirySettings(parsed, deadlines);
    }

    public InquirySettings {
        if (stages.isEmpty() || stages.size() != deadlines.size()) {
            throw new IllegalArgumentException("every inquiry stage needs exactly one deadline");
        }
        stages = stages.stream().map(List::copyOf).toList();
        deadlines = List.copyOf(deadlines);
    }
}
