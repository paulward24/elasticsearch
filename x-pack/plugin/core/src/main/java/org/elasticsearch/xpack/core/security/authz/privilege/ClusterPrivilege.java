/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.authz.privilege;

import org.apache.lucene.util.automaton.Automaton;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesAction;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsAction;
import org.elasticsearch.action.admin.cluster.snapshots.status.SnapshotsStatusAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateAction;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xpack.core.indexlifecycle.action.GetLifecycleAction;
import org.elasticsearch.xpack.core.indexlifecycle.action.GetStatusAction;
import org.elasticsearch.xpack.core.security.action.token.InvalidateTokenAction;
import org.elasticsearch.xpack.core.security.action.token.RefreshTokenAction;
import org.elasticsearch.xpack.core.security.action.user.HasPrivilegesAction;
import org.elasticsearch.xpack.core.security.support.Automatons;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static java.util.Map.entry;
import static org.elasticsearch.xpack.core.security.support.Automatons.minusAndMinimize;
import static org.elasticsearch.xpack.core.security.support.Automatons.patterns;

public final class ClusterPrivilege extends Privilege {

    // shared automatons
    private static final Automaton MANAGE_SECURITY_AUTOMATON = patterns("cluster:admin/xpack/security/*");
    private static final Automaton MANAGE_SAML_AUTOMATON = patterns("cluster:admin/xpack/security/saml/*",
            InvalidateTokenAction.NAME, RefreshTokenAction.NAME);
    private static final Automaton MANAGE_OIDC_AUTOMATON = patterns("cluster:admin/xpack/security/oidc/*");
    private static final Automaton MANAGE_TOKEN_AUTOMATON = patterns("cluster:admin/xpack/security/token/*");
    private static final Automaton MANAGE_API_KEY_AUTOMATON = patterns("cluster:admin/xpack/security/api_key/*");
    private static final Automaton MONITOR_AUTOMATON = patterns("cluster:monitor/*");
    private static final Automaton MONITOR_ML_AUTOMATON = patterns("cluster:monitor/xpack/ml/*");
    private static final Automaton MONITOR_DATA_FRAME_AUTOMATON = patterns("cluster:monitor/data_frame/*");
    private static final Automaton MONITOR_WATCHER_AUTOMATON = patterns("cluster:monitor/xpack/watcher/*");
    private static final Automaton MONITOR_ROLLUP_AUTOMATON = patterns("cluster:monitor/xpack/rollup/*");
    private static final Automaton ALL_CLUSTER_AUTOMATON = patterns("cluster:*", "indices:admin/template/*");
    private static final Automaton MANAGE_AUTOMATON = minusAndMinimize(ALL_CLUSTER_AUTOMATON, MANAGE_SECURITY_AUTOMATON);
    private static final Automaton MANAGE_ML_AUTOMATON = patterns("cluster:admin/xpack/ml/*", "cluster:monitor/xpack/ml/*");
    private static final Automaton MANAGE_DATA_FRAME_AUTOMATON = patterns("cluster:admin/data_frame/*", "cluster:monitor/data_frame/*");
    private static final Automaton MANAGE_WATCHER_AUTOMATON = patterns("cluster:admin/xpack/watcher/*", "cluster:monitor/xpack/watcher/*");
    private static final Automaton TRANSPORT_CLIENT_AUTOMATON = patterns("cluster:monitor/nodes/liveness", "cluster:monitor/state");
    private static final Automaton MANAGE_IDX_TEMPLATE_AUTOMATON = patterns("indices:admin/template/*");
    private static final Automaton MANAGE_INGEST_PIPELINE_AUTOMATON = patterns("cluster:admin/ingest/pipeline/*");
    private static final Automaton MANAGE_ROLLUP_AUTOMATON = patterns("cluster:admin/xpack/rollup/*", "cluster:monitor/xpack/rollup/*");
    private static final Automaton MANAGE_CCR_AUTOMATON =
        patterns("cluster:admin/xpack/ccr/*", ClusterStateAction.NAME, HasPrivilegesAction.NAME);
    private static final Automaton CREATE_SNAPSHOT_AUTOMATON = patterns(CreateSnapshotAction.NAME, SnapshotsStatusAction.NAME + "*",
            GetSnapshotsAction.NAME, SnapshotsStatusAction.NAME, GetRepositoriesAction.NAME);
    private static final Automaton READ_CCR_AUTOMATON = patterns(ClusterStateAction.NAME, HasPrivilegesAction.NAME);
    private static final Automaton MANAGE_ILM_AUTOMATON = patterns("cluster:admin/ilm/*");
    private static final Automaton READ_ILM_AUTOMATON = patterns(GetLifecycleAction.NAME, GetStatusAction.NAME);

    public static final ClusterPrivilege NONE =                  new ClusterPrivilege("none",                Automatons.EMPTY);
    public static final ClusterPrivilege ALL =                   new ClusterPrivilege("all",                 ALL_CLUSTER_AUTOMATON);
    public static final ClusterPrivilege MONITOR =               new ClusterPrivilege("monitor",             MONITOR_AUTOMATON);
    public static final ClusterPrivilege MONITOR_ML =            new ClusterPrivilege("monitor_ml",          MONITOR_ML_AUTOMATON);
    public static final ClusterPrivilege MONITOR_DATA_FRAME =
            new ClusterPrivilege("monitor_data_frame_transforms", MONITOR_DATA_FRAME_AUTOMATON);
    public static final ClusterPrivilege MONITOR_WATCHER =       new ClusterPrivilege("monitor_watcher",     MONITOR_WATCHER_AUTOMATON);
    public static final ClusterPrivilege MONITOR_ROLLUP =        new ClusterPrivilege("monitor_rollup",      MONITOR_ROLLUP_AUTOMATON);
    public static final ClusterPrivilege MANAGE =                new ClusterPrivilege("manage",              MANAGE_AUTOMATON);
    public static final ClusterPrivilege MANAGE_ML =             new ClusterPrivilege("manage_ml",           MANAGE_ML_AUTOMATON);
    public static final ClusterPrivilege MANAGE_DATA_FRAME =
            new ClusterPrivilege("manage_data_frame_transforms", MANAGE_DATA_FRAME_AUTOMATON);
    public static final ClusterPrivilege MANAGE_TOKEN =          new ClusterPrivilege("manage_token",        MANAGE_TOKEN_AUTOMATON);
    public static final ClusterPrivilege MANAGE_WATCHER =        new ClusterPrivilege("manage_watcher",      MANAGE_WATCHER_AUTOMATON);
    public static final ClusterPrivilege MANAGE_ROLLUP =         new ClusterPrivilege("manage_rollup",       MANAGE_ROLLUP_AUTOMATON);
    public static final ClusterPrivilege MANAGE_IDX_TEMPLATES =
            new ClusterPrivilege("manage_index_templates", MANAGE_IDX_TEMPLATE_AUTOMATON);
    public static final ClusterPrivilege MANAGE_INGEST_PIPELINES =
            new ClusterPrivilege("manage_ingest_pipelines", MANAGE_INGEST_PIPELINE_AUTOMATON);
    public static final ClusterPrivilege TRANSPORT_CLIENT =      new ClusterPrivilege("transport_client",    TRANSPORT_CLIENT_AUTOMATON);
    public static final ClusterPrivilege MANAGE_SECURITY =       new ClusterPrivilege("manage_security",     MANAGE_SECURITY_AUTOMATON);
    public static final ClusterPrivilege MANAGE_SAML =           new ClusterPrivilege("manage_saml",         MANAGE_SAML_AUTOMATON);
    public static final ClusterPrivilege MANAGE_OIDC =           new ClusterPrivilege("manage_oidc", MANAGE_OIDC_AUTOMATON);
    public static final ClusterPrivilege MANAGE_API_KEY =        new ClusterPrivilege("manage_api_key", MANAGE_API_KEY_AUTOMATON);
    public static final ClusterPrivilege MANAGE_PIPELINE =       new ClusterPrivilege("manage_pipeline", "cluster:admin/ingest/pipeline/*");
    public static final ClusterPrivilege MANAGE_CCR =            new ClusterPrivilege("manage_ccr", MANAGE_CCR_AUTOMATON);
    public static final ClusterPrivilege READ_CCR =              new ClusterPrivilege("read_ccr", READ_CCR_AUTOMATON);
    public static final ClusterPrivilege CREATE_SNAPSHOT =       new ClusterPrivilege("create_snapshot", CREATE_SNAPSHOT_AUTOMATON);
    public static final ClusterPrivilege MANAGE_ILM =            new ClusterPrivilege("manage_ilm", MANAGE_ILM_AUTOMATON);
    public static final ClusterPrivilege READ_ILM =              new ClusterPrivilege("read_ilm", READ_ILM_AUTOMATON);

    public static final Predicate<String> ACTION_MATCHER = ClusterPrivilege.ALL.predicate();

    private static final Map<String, ClusterPrivilege> VALUES = Map.ofEntries(
            entry("none", NONE),
            entry("all", ALL),
            entry("monitor", MONITOR),
            entry("monitor_ml", MONITOR_ML),
            entry("monitor_data_frame_transforms", MONITOR_DATA_FRAME),
            entry("monitor_watcher", MONITOR_WATCHER),
            entry("monitor_rollup", MONITOR_ROLLUP),
            entry("manage", MANAGE),
            entry("manage_ml", MANAGE_ML),
            entry("manage_data_frame_transforms", MANAGE_DATA_FRAME),
            entry("manage_token", MANAGE_TOKEN),
            entry("manage_watcher", MANAGE_WATCHER),
            entry("manage_index_templates", MANAGE_IDX_TEMPLATES),
            entry("manage_ingest_pipelines", MANAGE_INGEST_PIPELINES),
            entry("transport_client", TRANSPORT_CLIENT),
            entry("manage_security", MANAGE_SECURITY),
            entry("manage_saml", MANAGE_SAML),
            entry("manage_oidc", MANAGE_OIDC),
            entry("manage_api_key", MANAGE_API_KEY),
            entry("manage_pipeline", MANAGE_PIPELINE),
            entry("manage_rollup", MANAGE_ROLLUP),
            entry("manage_ccr", MANAGE_CCR),
            entry("read_ccr", READ_CCR),
            entry("create_snapshot", CREATE_SNAPSHOT),
            entry("manage_ilm", MANAGE_ILM),
            entry("read_ilm", READ_ILM));

    private static final ConcurrentHashMap<Set<String>, ClusterPrivilege> CACHE = new ConcurrentHashMap<>();

    private ClusterPrivilege(String name, String... patterns) {
        super(name, patterns);
    }

    private ClusterPrivilege(String name, Automaton automaton) {
        super(Collections.singleton(name), automaton);
    }

    private ClusterPrivilege(Set<String> name, Automaton automaton) {
        super(name, automaton);
    }

    public static ClusterPrivilege get(Set<String> name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }
        return CACHE.computeIfAbsent(name, ClusterPrivilege::resolve);
    }

    private static ClusterPrivilege resolve(Set<String> name) {
        final int size = name.size();
        if (size == 0) {
            throw new IllegalArgumentException("empty set should not be used");
        }

        Set<String> actions = new HashSet<>();
        Set<Automaton> automata = new HashSet<>();
        for (String part : name) {
            part = part.toLowerCase(Locale.ROOT);
            if (ACTION_MATCHER.test(part)) {
                actions.add(actionToPattern(part));
            } else {
                ClusterPrivilege privilege = VALUES.get(part);
                if (privilege != null && size == 1) {
                    return privilege;
                } else if (privilege != null) {
                    automata.add(privilege.automaton);
                } else {
                    throw new IllegalArgumentException("unknown cluster privilege [" + name + "]. a privilege must be either " +
                            "one of the predefined fixed cluster privileges [" +
                            Strings.collectionToCommaDelimitedString(VALUES.entrySet()) + "] or a pattern over one of the available " +
                            "cluster actions");
                }
            }
        }

        if (actions.isEmpty() == false) {
            automata.add(patterns(actions));
        }
        return new ClusterPrivilege(name, Automatons.unionAndMinimize(automata));
    }
}
