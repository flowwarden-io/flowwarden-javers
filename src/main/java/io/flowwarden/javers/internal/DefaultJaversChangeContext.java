package io.flowwarden.javers.internal;

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.stream.ChangeStreamContext;
import org.bson.BsonDocument;
import org.javers.core.commit.CommitMetadata;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.core.metamodel.object.SnapshotType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation that wraps a Javers {@link CdoSnapshot} and delegates
 * FlowWarden operations to the underlying {@link ChangeStreamContext}.
 */
public class DefaultJaversChangeContext<T> implements JaversChangeContext<T> {

    private final CdoSnapshot snapshot;
    private final ChangeStreamContext<?> delegate;

    public DefaultJaversChangeContext(CdoSnapshot snapshot, ChangeStreamContext<?> delegate) {
        this.snapshot = snapshot;
        this.delegate = delegate;
    }

    // ---- Javers snapshot data ----

    @Override
    public CdoSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public SnapshotType getSnapshotType() {
        return snapshot.getType();
    }

    @Override
    public CommitMetadata getCommitMetadata() {
        return snapshot.getCommitMetadata();
    }

    @Override
    public List<String> getChangedProperties() {
        return snapshot.getChanged();
    }

    @Override
    public long getVersion() {
        return snapshot.getVersion();
    }

    @Override
    public String getEntityId() {
        return snapshot.getGlobalId().value();
    }

    // ---- FlowWarden delegates ----

    @Override
    public String getEventId() {
        return delegate.getEventId();
    }

    @Override
    public Instant getClusterTime() {
        return delegate.getClusterTime();
    }

    @Override
    public BsonDocument getResumeToken() {
        return delegate.getResumeToken();
    }

    @Override
    public void saveCheckpointNow() {
        delegate.saveCheckpointNow();
    }

    @Override
    public void sendToDlq(String reason) {
        delegate.sendToDlq(reason);
    }

    @Override
    public void addMetadata(String key, Object value) {
        delegate.addMetadata(key, value);
    }

    @Override
    public <V> Optional<V> getMetadata(String key, Class<V> type) {
        return delegate.getMetadata(key, type);
    }

    @Override
    public Map<String, Object> getAllMetadata() {
        return delegate.getAllMetadata();
    }
}
