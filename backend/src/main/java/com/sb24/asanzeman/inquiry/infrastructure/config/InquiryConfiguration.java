package com.sb24.asanzeman.inquiry.infrastructure.config;

import com.sb24.asanzeman.inquiry.application.InquiryChain;
import com.sb24.asanzeman.inquiry.application.InquiryRunService;
import com.sb24.asanzeman.inquiry.application.InquiryRunSettings;
import com.sb24.asanzeman.inquiry.application.InquirySettings;
import com.sb24.asanzeman.inquiry.application.StageRunner;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryHandler;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryRunRepositoryPort;
import com.sb24.asanzeman.inquiry.domain.port.out.RunEventsPort;
import com.sb24.asanzeman.inquiry.infrastructure.external.BlacklistHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.BoardRelationHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.ExportCommitmentHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.ImportCommitmentHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.SabtAhvalHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.SamaChequeHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.SamatHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.SepadHandler;
import com.sb24.asanzeman.inquiry.infrastructure.external.ShahkarHandler;
import com.sb24.asanzeman.integration.inquiry.config.InquiryClients;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The inquiry module's wiring: the nine inquiry handlers, the two-stage chain, and the run service whose background
 * runs each get a virtual thread (D24). The chain refuses to start unless every configured inquiry has a handler.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InquiryProperties.class)
public class InquiryConfiguration {

    @Bean
    InquirySettings inquirySettings(InquiryProperties p) {
        return InquirySettings.parse(p.stages(), p.stageDeadlines());
    }

    @Bean
    InquiryRunSettings inquiryRunSettings(InquiryProperties p) {
        return new InquiryRunSettings(p.staleRunAfter(), p.customerRetryCooldown(), p.passedResultReuse());
    }

    @Bean
    InquiryHandler sabtAhvalHandler(InquiryClients clients) {
        return new SabtAhvalHandler(clients.create("sabt-ahval"));
    }

    @Bean
    InquiryHandler shahkarHandler(InquiryClients clients) {
        return new ShahkarHandler(clients.create("shahkar"));
    }

    @Bean
    InquiryHandler sepadHandler(InquiryClients clients, Clock clock) {
        return new SepadHandler(clients.create("sepad"), clock);
    }

    @Bean
    InquiryHandler blacklistHandler(InquiryClients clients) {
        return new BlacklistHandler(clients.create("blacklist"));
    }

    @Bean
    InquiryHandler samaChequeHandler(InquiryClients clients) {
        return new SamaChequeHandler(clients.create("sama-cheque"));
    }

    @Bean
    InquiryHandler samatHandler(InquiryClients clients) {
        return new SamatHandler(clients.create("samat"));
    }

    @Bean
    InquiryHandler boardRelationHandler(InquiryClients clients) {
        return new BoardRelationHandler(clients.create("board-relation"));
    }

    @Bean
    InquiryHandler importCommitmentHandler(InquiryClients clients) {
        return new ImportCommitmentHandler(clients.create("import-commitment"));
    }

    @Bean
    InquiryHandler exportCommitmentHandler(InquiryClients clients) {
        return new ExportCommitmentHandler(clients.create("export-commitment"));
    }

    @Bean
    InquiryChain inquiryChain(List<InquiryHandler> handlers, StageRunner stages, InquirySettings settings) {
        return new InquiryChain(handlers, stages, settings);
    }

    @Bean
    InquiryRunService inquiryRunService(InquiryChain chain, InquiryRunRepositoryPort runs, RunEventsPort events,
            Clock clock, InquiryRunSettings settings) {
        // one virtual thread per run, not a shared bean: nothing else should pick this executor up by type
        return new InquiryRunService(chain, runs, events, Executors.newVirtualThreadPerTaskExecutor(), clock,
                settings);
    }
}
