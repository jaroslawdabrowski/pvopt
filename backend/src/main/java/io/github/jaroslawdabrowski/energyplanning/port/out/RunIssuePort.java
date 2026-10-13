package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;

import java.util.Optional;

public interface RunIssuePort {

    void record(RunIssue issue);

    Optional<RunIssue> findLatest();
}
