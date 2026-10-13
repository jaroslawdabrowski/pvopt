package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.port.out.RunIssuePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class PanacheRunIssueRepository implements RunIssuePort {

    @Override
    @Transactional
    public void record(RunIssue issue) {
        var entity = new RunIssueEntity();
        entity.occurredAt = issue.occurredAt();
        entity.window = issue.window().name();
        entity.kind = issue.kind().name();
        String detail = issue.detail() == null ? "" : issue.detail();
        entity.detail = detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        entity.persist();
    }

    @Override
    public Optional<RunIssue> findLatest() {
        return RunIssueEntity.<RunIssueEntity>find("order by occurredAt desc").firstResultOptional()
                .map(e -> new RunIssue(e.occurredAt, ChargeWindow.valueOf(e.window), RunIssue.Kind.valueOf(e.kind),
                        e.detail));
    }
}
